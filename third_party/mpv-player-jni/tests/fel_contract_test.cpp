#include "node_json.h"
#include "filters/f_android_fel.h"
#include <cassert>
#include <limits>
#include <iostream>

static mpv_node integer(long long value) {
    mpv_node node{};
    node.format = MPV_FORMAT_INT64;
    node.u.int64 = value;
    return node;
}

int main() {
    std::string json;
    mpv_node number = integer(9223372036854775807LL);
    assert(mpv_node_json::encode(&number, &json));
    assert(json == "9223372036854775807");

    mpv_node title{};
    title.format = MPV_FORMAT_STRING;
    title.u.string = const_cast<char *>("国语 🎬\n\t\"\\");
    assert(mpv_node_json::encode(&title, &json));
    assert(json == "\"国语 🎬\\u000a\\u0009\\\"\\\\\"");

    char *keys[] = {const_cast<char *>("id"), const_cast<char *>("title")};
    mpv_node values[] = {integer(2), title};
    mpv_node_list fields{2, values, keys};
    mpv_node map{};
    map.format = MPV_FORMAT_NODE_MAP;
    map.u.list = &fields;
    mpv_node_list list{1, &map, nullptr};
    mpv_node array{};
    array.format = MPV_FORMAT_NODE_ARRAY;
    array.u.list = &list;
    assert(mpv_node_json::encode(&array, &json));
    assert(json.rfind("[{\"id\":2,\"title\":", 0) == 0);

    mpv_node floating{};
    floating.format = MPV_FORMAT_DOUBLE;
    floating.u.double_ = std::numeric_limits<double>::infinity();
    assert(mpv_node_json::encode(&floating, &json) && json == "null");
    floating.u.double_ = 1.25;
    assert(mpv_node_json::encode(&floating, &json) && json == "1.25");

    // Malformed lists, cycles, large strings and unsupported types fail closed.
    list.num = -1;
    assert(!mpv_node_json::encode(&array, &json) && json.empty());
    list.num = 1;
    list.values = &array;
    assert(!mpv_node_json::encode(&array, &json) && json.empty());
    std::string oversized(mpv_node_json::MAX_BYTES, 'x');
    title.u.string = oversized.data();
    assert(!mpv_node_json::encode(&title, &json) && json.empty());
    title.format = MPV_FORMAT_BYTE_ARRAY;
    assert(!mpv_node_json::encode(&title, &json));
    assert(!mpv_node_json::encode(nullptr, &json));

    // EL can spend an arbitrary warm-up interval decoding/reordering while the
    // BL output is held. A full one-frame queue must never cause BL-only output.
    static_assert(MP_ANDROID_FEL_BL_PENDING == 1);
    static_assert(MP_ANDROID_FEL_BL_PREFETCH == 1);
    static_assert(MP_ANDROID_FEL_EL_PREFETCH > 0);
    for (int iteration = 0; iteration < 10000; ++iteration)
        assert(!mp_android_fel_can_skip_bl(false, false, 0));
    assert(!mp_android_fel_can_skip_bl(false, true, -1)); // discard old EL, not BL
    assert(!mp_android_fel_can_skip_bl(false, true, 0));  // exact pair
    assert(mp_android_fel_can_skip_bl(false, true, 1));   // affirmative missing partner
    assert(mp_android_fel_can_skip_bl(true, false, 0));  // EL EOF
    for (int errors = 0; errors < MP_ANDROID_FEL_ERROR_LIMIT; ++errors)
        assert(!mp_android_fel_decode_failed(true, errors));
    assert(mp_android_fel_decode_failed(true, MP_ANDROID_FEL_ERROR_LIMIT));
    assert(!mp_android_fel_decode_failed(false, 100));
    assert(!mp_android_fel_decode_failed(true, 0)); // successful frame/seek resets budget
    assert(mp_android_fel_stage_output(true, true));
    assert(!mp_android_fel_stage_output(false, true)); // ordinary direct playback unchanged
    assert(!mp_android_fel_stage_output(true, false)); // explicit conversion path unchanged
    assert(!mp_android_fel_preserve_depth(true, 8));
    assert(mp_android_fel_preserve_depth(true, 10));
    assert(mp_android_fel_preserve_depth(true, 16));
    assert(mp_android_fel_preserve_depth(false, 8)); // existing SDR policy unchanged
    static_assert(MP_ANDROID_FEL_COPY_WAIT_NS > 0 && MP_ANDROID_FEL_COPY_WAIT_NS <= 100000000LL);
    std::cout << "PASS: bounded NODE serialization, FEL warm-up/backpressure, failure budget and GPU staging policy\n";
}
