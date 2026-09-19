#pragma once

#include <mpv/client.h>
#include <cmath>
#include <cstdio>
#include <new>
#include <string>

// Copy a NODE while mpv_wait_event still owns it. Never expose mpv pointers to
// Java, and bound both traversal and allocation for untrusted track metadata.
namespace mpv_node_json {
constexpr size_t MAX_BYTES = 512 * 1024;
constexpr int MAX_DEPTH = 12;
constexpr int MAX_NODES = 16384;

class Writer {
    std::string text;
    int remaining = MAX_NODES;

    bool append(const char *s, size_t n) {
        if (n > MAX_BYTES - text.size()) return false;
        text.append(s, n);
        return true;
    }

    bool quote(const char *s) {
        if (!s || !append("\"", 1)) return false;
        for (size_t i = 0; s[i]; ++i) {
            unsigned char c = static_cast<unsigned char>(s[i]);
            if (c == '"' || c == '\\') {
                char escaped[] = {'\\', static_cast<char>(c)};
                if (!append(escaped, 2)) return false;
            } else if (c < 0x20) {
                char escaped[7];
                std::snprintf(escaped, sizeof(escaped), "\\u%04x", c);
                if (!append(escaped, 6)) return false;
            } else if (!append(s + i, 1)) {
                return false;
            }
        }
        return append("\"", 1);
    }

    bool node(const mpv_node &value, int depth) {
        if (depth > MAX_DEPTH || --remaining < 0) return false;
        char number[64];
        switch (value.format) {
        case MPV_FORMAT_NONE: return append("null", 4);
        case MPV_FORMAT_STRING: return quote(value.u.string);
        case MPV_FORMAT_FLAG:
            return value.u.flag ? append("true", 4) : append("false", 5);
        case MPV_FORMAT_INT64: {
            int n = std::snprintf(number, sizeof(number), "%lld",
                                  static_cast<long long>(value.u.int64));
            return n > 0 && n < static_cast<int>(sizeof(number)) && append(number, n);
        }
        case MPV_FORMAT_DOUBLE: {
            if (!std::isfinite(value.u.double_)) return append("null", 4);
            // snprintf is locale-dependent; avoid emitting a comma decimal.
            int n = std::snprintf(number, sizeof(number), "%.17g", value.u.double_);
            for (int i = 0; i < n && i < static_cast<int>(sizeof(number)); ++i)
                if (number[i] == ',') number[i] = '.';
            return n > 0 && n < static_cast<int>(sizeof(number)) && append(number, n);
        }
        case MPV_FORMAT_NODE_ARRAY:
        case MPV_FORMAT_NODE_MAP: {
            const mpv_node_list *list = value.u.list;
            bool map = value.format == MPV_FORMAT_NODE_MAP;
            if (!list || list->num < 0 || list->num > remaining ||
                (list->num && (!list->values || (map && !list->keys)))) return false;
            if (!append(map ? "{" : "[", 1)) return false;
            for (int i = 0; i < list->num; ++i) {
                if (i && !append(",", 1)) return false;
                if (map && (!quote(list->keys[i]) || !append(":", 1))) return false;
                if (!node(list->values[i], depth + 1)) return false;
            }
            return append(map ? "}" : "]", 1);
        }
        default: return false;
        }
    }

public:
    bool write(const mpv_node *value, std::string *result) {
        if (!result) return false;
        result->clear();
        if (!value || !node(*value, 0)) return false;
        result->swap(text);
        return true;
    }
};

inline bool encode(const mpv_node *value, std::string *result) {
    try {
        return Writer().write(value, result);
    } catch (const std::bad_alloc &) {
        if (result) result->clear();
        return false;
    }
}
}
