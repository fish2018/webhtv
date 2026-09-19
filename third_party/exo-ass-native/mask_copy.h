#pragma once

#include <cstddef>
#include <cstdint>
#include <cstring>
#include <limits>
#include <vector>

namespace exo_ass {
// libass guarantees only stride * (height - 1) + width accessible source bytes.
inline bool copy_mask(std::vector<uint8_t> &output, const uint8_t *source,
                      int width, int height, int stride, size_t budget) {
    if (!source || width <= 0 || height <= 0 || stride < width
            || static_cast<size_t>(width) > budget / static_cast<size_t>(height)
            || static_cast<size_t>(height - 1)
                    > (std::numeric_limits<size_t>::max() - width) / static_cast<size_t>(stride))
        return false;
    output.resize(static_cast<size_t>(width) * height);
    for (int row = 0; row < height; ++row)
        std::memcpy(output.data() + static_cast<size_t>(row) * width,
                    source + static_cast<size_t>(row) * stride, width);
    return true;
}
} // namespace exo_ass
