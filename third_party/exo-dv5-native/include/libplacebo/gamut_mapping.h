/*
 * This file is part of libplacebo.
 *
 * libplacebo is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * libplacebo is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with libplacebo.  If not, see <http://www.gnu.org/licenses/>.
 */

#ifndef LIBPLACEBO_GAMUT_MAPPING_H_
#define LIBPLACEBO_GAMUT_MAPPING_H_

#include <libplacebo/common.h>
#include <libplacebo/colorspace.h>

PL_API_BEGIN

struct pl_gamut_map_params;
struct pl_gamut_map_function {
    const char *name;        // Identifier
    const char *description; // Friendly / longer name

    // The gamut-mapping function itself. Iterates over all values in `lut`,
    // and adapts them as needed.
    void (*map)(float *lut, const struct pl_gamut_map_params *params);

    // Returns true if `map` supports both stretching and contracting the
    // gamut. In this case, `map` is always executed, even if the output gamut
    // is larger than the input gamut.
    bool bidirectional;

    // Private data. Unused by libplacebo, but may be accessed by `map`.
    void *priv;
};

struct pl_gamut_map_constants {
    // (Relative) chromaticity protection zone for perceptual mapping [0,1]
    float perceptual_deadzone;

    // Strength of the perceptual saturation mapping component [0,1]
    float perceptual_strength;

    // I vs C curve gamma to use for colorimetric clipping [0,10]
    float colorimetric_gamma;

    // Knee point to use for softclipping methods (perceptual, softclip) [0,1]
    float softclip_knee;

    // Desaturation strength (for softclip only) [0,1]
    float softclip_desat;
};

#define PL_GAMUT_MAP_CONSTANTS    \
    .colorimetric_gamma  = 1.80f, \
    .softclip_knee       = 0.70f, \
    .softclip_desat      = 0.35f, \
    .perceptual_deadzone = 0.30f, \
    .perceptual_strength = 0.80f,

struct pl_gamut_map_params {
    // If `function` is NULL, defaults to `pl_gamut_map_clip`.
    const struct pl_gamut_map_function *function;

    // The desired input/output primaries. This affects the subjective color
    // volume in which the desired mapping shall take place.
    struct pl_raw_primaries input_gamut;
    struct pl_raw_primaries output_gamut;

    // Minimum/maximum luminance (PQ) of the target display. Note that the same
    // value applies to both the input and output, since it's assumed that tone
    // mapping has already happened by this stage. This effectively defines the
    // legal gamut boundary in RGB space.
    //
    // This also defines the I channel value range, for `pl_gamut_map_generate`
    float min_luma;
    float max_luma;

    // Common constants, should be initialized to PL_GAMUT_MAP_CONSTANTS if
    // not intending to override them further.
    struct pl_gamut_map_constants constants;

    // -- LUT generation options (for `pl_gamut_map_generate` only)

    // The size of the resulting LUT, per channel.
    //
    // Note: For quality, it's generally best to increase h > I > C
    int lut_size_I;
    int lut_size_C;
    int lut_size_h;

    // The stride (in number of floats) between elements in the resulting LUT.
    int lut_stride;

    // -- Removed parameters
    PL_DEPRECATED_IN(v6.289) float chroma_margin; // non-functional
};

#define pl_gamut_map_params(...) (&(struct pl_gamut_map_params) {   \
    .constants = { PL_GAMUT_MAP_CONSTANTS },                        \
    __VA_ARGS__                                                     \
})

// Note: Only does pointer equality testing on `function`
PL_API bool pl_gamut_map_params_equal(const struct pl_gamut_map_params *a,
                                      const struct pl_gamut_map_params *b);

// Returns true if the given gamut mapping configuration effectively represents
// a no-op configuration. Gamut mapping can be skipped in this case.
PL_API bool pl_gamut_map_params_noop(const struct pl_gamut_map_params *params);

// Generate a gamut-mapping LUT for a given configuration. LUT samples are
// stored as IPTPQc4 values, but the LUT itself is indexed by IChPQc4,spanning
// the effective range [min_luma, max_luma] × [0, 0.5] × [-pi,pi].
//
// This ordering is designed to keep frequently co-occurring values close in
// memory, while permitting simple wrapping of the 'h' component.
PL_API void pl_gamut_map_generate(float *out, const struct pl_gamut_map_params *params);

// Samples a gamut mapping function for a single IPTPQc4 value. The input
// values are updated in-place.
PL_API void pl_gamut_map_sample(float x[3], const struct pl_gamut_map_params *params);

// Performs no gamut-mapping, just hard clips out-of-range colors per-channel.
PL_API extern const struct pl_gamut_map_function pl_gamut_map_clip;

// Performs a perceptually balanced (saturation) gamut mapping, using a soft
// knee function to preserve in-gamut colors, followed by a final softclip
// operation. This works bidirectionally, meaning it can both compress and
// expand the gamut. Behaves similar to a blend of `saturation` and `softclip`.
PL_API extern const struct pl_gamut_map_function pl_gamut_map_perceptual;

// Performs a perceptually balanced gamut mapping using a soft knee function to
// roll-off clipped regions, and a hue shifting function to preserve saturation.
PL_API extern const struct pl_gamut_map_function pl_gamut_map_softclip;

// Performs relative colorimetric clipping, while maintaining an exponential
// relationship between brightness and chromaticity.
PL_API extern const struct pl_gamut_map_function pl_gamut_map_relative;

// Performs simple RGB->RGB saturation mapping. The input R/G/B channels are
// mapped directly onto the output R/G/B channels. Will never clip, but will
// distort all hues and/or result in a faded look.
PL_API extern const struct pl_gamut_map_function pl_gamut_map_saturation;

// Performs absolute colorimetric clipping. Like pl_gamut_map_relative, but
// does not adapt the white point.
PL_API extern const struct pl_gamut_map_function pl_gamut_map_absolute;

// Performs constant-luminance colorimetric clipping, desaturing colors
// towards white until they're in-range.
PL_API extern const struct pl_gamut_map_function pl_gamut_map_desaturate;

// Uniformly darkens the input slightly to prevent clipping on blown-out
// highlights, then clamps colorimetrically to the input gamut boundary,
// biased slightly to preserve chromaticity over luminance.
PL_API extern const struct pl_gamut_map_function pl_gamut_map_darken;

// Performs no gamut mapping, but simply highlights out-of-gamut pixels.
PL_API extern const struct pl_gamut_map_function pl_gamut_map_highlight;

// Linearly/uniformly desaturates the image in order to bring the entire
// image into the target gamut.
PL_API extern const struct pl_gamut_map_function pl_gamut_map_linear;

// A list of built-in gamut mapping functions, terminated by NULL
PL_API extern const struct pl_gamut_map_function * const pl_gamut_map_functions[];
PL_API extern const int pl_num_gamut_map_functions; // excluding trailing NULL

// Find the gamut mapping function with the given name, or NULL on failure.
PL_API const struct pl_gamut_map_function *pl_find_gamut_map_function(const char *name);

PL_API_END

#endif // LIBPLACEBO_GAMUT_MAPPING_H_
