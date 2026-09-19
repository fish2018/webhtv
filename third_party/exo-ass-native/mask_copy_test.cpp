#include "mask_copy.h"
#include <cassert>
#include <climits>
#include <memory>

int main() {
    // Allocate exactly the libass guarantee: ASan catches a final-row overread.
    for (int width : {1, 3, 16, 255}) for (int height : {1, 2, 19})
        for (int padding : {0, 1, 31}) {
            int stride = width + padding;
            size_t length = static_cast<size_t>(stride) * (height - 1) + width;
            auto source = std::make_unique<uint8_t[]>(length);
            for (size_t i = 0; i < length; ++i) source[i] = static_cast<uint8_t>(i);
            std::vector<uint8_t> output;
            assert(exo_ass::copy_mask(output, source.get(), width, height, stride, 8192));
            assert(output.size() == static_cast<size_t>(width) * height);
            for (int y = 0; y < height; ++y) for (int x = 0; x < width; ++x)
                assert(output[static_cast<size_t>(y) * width + x] == source[static_cast<size_t>(y) * stride + x]);
        }
    std::vector<uint8_t> output = {42};
    uint8_t source[] = {1, 2, 3};
    assert(!exo_ass::copy_mask(output, nullptr, 1, 1, 1, 8));
    assert(!exo_ass::copy_mask(output, source, -1, 1, 1, 8));
    assert(!exo_ass::copy_mask(output, source, 1, 0, 1, 8));
    assert(!exo_ass::copy_mask(output, source, 3, 1, 2, 8));
    assert(!exo_ass::copy_mask(output, source, 3, 3, 3, 8));
    assert(!exo_ass::copy_mask(output, source, INT_MAX, INT_MAX, INT_MAX, 8 * 1024 * 1024));
    assert(output.size() == 1 && output[0] == 42);
}
