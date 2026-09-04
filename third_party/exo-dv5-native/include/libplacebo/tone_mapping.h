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

#ifndef LIBPLACEBO_TONE_MAPPING_H_
#define LIBPLACEBO_TONE_MAPPING_H_

#include <stddef.h>
#include <stdbool.h>

#include <libplacebo/common.h>
#include <libplacebo/colorspace.h>

PL_API_BEGIN

struct pl_tone_map_params;
struct pl_tone_map_function {
    const char *name;        // Identifier
    const char *description; // Friendly / longer name

    // This controls the type of values input/output to/from `map`
    enum pl_hdr_scaling scaling;

    // The tone-mapping function itself. Iterates over all values in `lut`, and
    // adapts them as needed.
    //
    // Note that the `params` struct fed into this function is guaranteed to
    // satisfy `params->input_scaling == params->output_scaling == scaling`,
    // and also obeys `params->input_max >= params->output_max`.
    void (*map)(float *lut, const struct pl_tone_map_params *params);

    // Inverse tone mapping function. Optional. If absent, this tone mapping
    // curve only works in the forwards direction.
    //
    // For this function, `params->input_max <= params->output_max`.
    void (*map_inverse)(float *lut, const struct pl_tone_map_params *params);

    // Private data. Unused by libplacebo, but may be accessed by `map`.
    void *priv;

    // --- Deprecated fields
    PL_DEPRECATED_IN(v6.311) const char *param_desc;
    PL_DEPRECATED_IN(v6.311) float param_min;
    PL_DEPRECATED_IN(v6.311) float param_def;
    PL_DEPRECATED_IN(v6.311) float param_max;
};

struct pl_tone_map_constants {
    // Configures the knee point, as a ratio between the source average and
    // target average (in PQ space). An adaptation of 1.0 always adapts the
    // source scene average brightness to the (scaled) target average,
    // while a value of 0.0 never modifies scene brightness. [0,1]
    //
    // Affects all methods that use the ST2094 knee point determination
    // (currently ST2094-40, ST2094-10 and spline)
    float knee_adaptation;

    // Configures the knee point minimum and maximum, respectively, as
    // a percentage of the PQ luminance range. Provides a hard limit on the
    // knee point chosen by `knee_adaptation`.
    float knee_minimum; // (0, 0.5)
    float knee_maximum; // (0.5, 1.0)

    // Default knee point to use in the absence of source scene average
    // metadata. Normally, this is ignored in favor of picking the knee
    // point as the (relative) source scene average brightness level.
    float knee_default; // [knee_minimum, knee_maximum]

    // Knee point offset (for BT.2390 only). Note that a value of 0.5 is
    // the spec-defined default behavior, which differs from the libplacebo
    // default of 1.0. [0.5, 2]
    float knee_offset;

    // For the single-pivot polynomial (spline) function, this controls the
    // coefficients used to tune the slope of the curve. This tuning is designed
    // to make the slope closer to 1.0 when the difference in peaks is low,
    // and closer to linear when the difference between peaks is high.
    float slope_tuning;   // [0,10]
    float slope_offset;   // [0,1]

    // Contrast setting for the spline function. Higher values make the curve
    // steeper (closer to `clip`), preserving midtones at the cost of losing
    // shadow/highlight details, while lower values make the curve shallowed
    // (closer to `linear`), preserving highlights at the cost of losing midtone
    // contrast. Values above 1.0 are possible, resulting in an output with more
    // contrast than the input.
    float spline_contrast; // [0,1.5]

    // For the reinhard function, this specifies the local contrast coefficient
    // at the display peak. Essentially, a value of 0.5 implies that the
    // reference white will be about half as bright as when clipping. (0,1)
    float reinhard_contrast;

    // For legacy functions (mobius, gamma) which operate on linear light, this
    // directly sets the corresponding knee point. (0,1)
    float linear_knee;

    // For linear methods (linear, linearlight), this controls the linear
    // exposure/gain applied to the image. (0,10]
    float exposure;
};

#define PL_TONE_MAP_CONSTANTS  \
    .knee_adaptation   = 0.4f, \
    .knee_minimum      = 0.1f, \
    .knee_maximum      = 0.8f, \
    .knee_default      = 0.4f, \
    .knee_offset       = 1.0f, \
    .slope_tuning      = 1.5f, \
    .slope_offset      = 0.2f, \
    .spline_contrast   = 0.5f, \
    .reinhard_contrast = 0.5f, \
    .linear_knee       = 0.3f, \
    .exposure          = 1.0f,

struct pl_tone_map_params {
    // If `function` is NULL, defaults to `pl_tone_map_clip`.
    const struct pl_tone_map_function *function;

    // Common constants, should be initialized to PL_TONE_MAP_CONSTANTS if
    // not intending to override them further.
    struct pl_tone_map_constants constants;

    // The desired input/output scaling of the tone map. If this differs from
    // `function->scaling`, any required conversion will be performed.
    //
    // Note that to maximize LUT efficiency, it's *highly* recommended to use
    // either PL_HDR_PQ or PL_HDR_SQRT as the input scaling, except when
    // using `pl_tone_map_sample`.
    enum pl_hdr_scaling input_scaling;
    enum pl_hdr_scaling output_scaling;

    // The size of the resulting LUT. (For `pl_tone_map_generate` only)
    size_t lut_size;

    // The characteristics of the input, in `input_scaling` units.
    float input_min;
    float input_max;
    float input_avg; // or 0 if unknown

    // The desired characteristics of the output, in `output_scaling` units.
    float output_min;
    float output_max;

    // The input HDR metadata. Only used by a select few tone-mapping
    // functions, currently only SMPTE ST2094. (Optional)
    struct pl_hdr_metadata hdr;

    // --- Deprecated fields
    PL_DEPRECATED_IN(v6.311) float param; // see `constants`
};

#define pl_tone_map_params(...) (&(struct pl_tone_map_params) { __VA_ARGS__ });

// Note: Only does pointer equality testing on `function`
PL_API bool pl_tone_map_params_equal(const struct pl_tone_map_params *a,
                                     const struct pl_tone_map_params *b);

// Clamps/defaults the parameters, including input/output maximum.
PL_API void pl_tone_map_params_infer(struct pl_tone_map_params *params);

// Returns true if the given tone mapping configuration effectively represents
// a no-op configuration. Tone mapping can be skipped in this case (although
// strictly speaking, the LUT would still clip illegal input values)
PL_API bool pl_tone_map_params_noop(const struct pl_tone_map_params *params);

// Generate a tone-mapping LUT for a given configuration. This will always
// span the entire input range, as given by `input_min` and `input_max`.
PL_API void pl_tone_map_generate(float *out, const struct pl_tone_map_params *params);

// Samples a tone mapping function at a single position. Note that this is less
// efficient than `pl_tone_map_generate` for generating multiple values.
//
// Ignores `params->lut_size`.
PL_API float pl_tone_map_sample(float x, const struct pl_tone_map_params *params);

// Performs no tone-mapping, just clips out-of-range colors. Retains perfect
// color accuracy for in-range colors but completely destroys out-of-range
// information. Does not perform any black point adaptation.
PL_API extern const struct pl_tone_map_function pl_tone_map_clip;

// EETF from SMPTE ST 2094-40 Annex B, which uses the provided OOTF based on
// Bezier curves to perform tone-mapping. The OOTF used is adjusted based on
// the ratio between the targeted and actual display peak luminances. In the
// absence of HDR10+ metadata, falls back to a simple constant bezier curve.
PL_API extern const struct pl_tone_map_function pl_tone_map_st2094_40;

// EETF from SMPTE ST 2094-10 Annex B.2, which takes into account the input
// signal average luminance in addition to the maximum/minimum.
//
// Note: This does *not* currently include the subjective gain/offset/gamma
// controls defined in Annex B.3. (Open an issue with a valid sample file if
// you want such parameters to be respected.)
PL_API extern const struct pl_tone_map_function pl_tone_map_st2094_10;

// EETF from the ITU-R Report BT.2390, a hermite spline roll-off with linear
// segment.
PL_API extern const struct pl_tone_map_function pl_tone_map_bt2390;

// EETF from ITU-R Report BT.2446, method A. Can be used for both forward
// and inverse tone mapping.
PL_API extern const struct pl_tone_map_function pl_tone_map_bt2446a;

// Simple spline consisting of two polynomials, joined by a single pivot point,
// which is tuned based on the source scene average brightness (taking into
// account dynamic metadata if available). This function can be used
// for both forward and inverse tone mapping.
PL_API extern const struct pl_tone_map_function pl_tone_map_spline;

// Very simple non-linear curve. Named after Erik Reinhard.
PL_API extern const struct pl_tone_map_function pl_tone_map_reinhard;

// Generalization of the reinhard tone mapping algorithm to support an
// additional linear slope near black. The name is derived from its function
// shape (ax+b)/(cx+d), which is known as a Möbius transformation.
PL_API extern const struct pl_tone_map_function pl_tone_map_mobius;

// Piece-wise, filmic tone-mapping algorithm developed by John Hable for use in
// Uncharted 2, inspired by a similar tone-mapping algorithm used by Kodak.
// Popularized by its use in video games with HDR rendering. Preserves both
// dark and bright details very well, but comes with the drawback of changing
// the average brightness quite significantly. This is sort of similar to
// pl_tone_map_reinhard with `reinhard_contrast=0.24`.
PL_API extern const struct pl_tone_map_function pl_tone_map_hable;

// Fits a gamma (power) function to transfer between the source and target
// color spaces, effectively resulting in a perceptual hard-knee joining two
// roughly linear sections. This preserves details at all scales, but can result
// in an image with a muted or dull appearance.
PL_API extern const struct pl_tone_map_function pl_tone_map_gamma;

// Linearly stretches the input range to the output range, in PQ space. This
// will preserve all details accurately, but results in a significantly
// different average brightness. Can be used for inverse tone-mapping in
// addition to regular tone-mapping.
PL_API extern const struct pl_tone_map_function pl_tone_map_linear;

// Like `pl_tone_map_linear`, but in linear light (instead of PQ). Works well
// for small range adjustments but may cause severe darkening when
// downconverting from e.g. 10k nits to SDR.
PL_API extern const struct pl_tone_map_function pl_tone_map_linear_light;

// A list of built-in tone mapping functions, terminated by NULL
PL_API extern const struct pl_tone_map_function * const pl_tone_map_functions[];
PL_API extern const int pl_num_tone_map_functions; // excluding trailing NULL

// Find the tone mapping function with the given name, or NULL on failure.
PL_API const struct pl_tone_map_function *pl_find_tone_map_function(const char *name);

// Deprecated alias, do not use
#define pl_tone_map_auto pl_tone_map_spline

PL_API_END

#endif // LIBPLACEBO_TONE_MAPPING_H_
