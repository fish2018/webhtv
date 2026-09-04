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

#ifndef LIBPLACEBO_SHADERS_DITHERING_H_
#define LIBPLACEBO_SHADERS_DITHERING_H_

// Dithering shaders

#include <libplacebo/colorspace.h>
#include <libplacebo/dither.h>
#include <libplacebo/shaders.h>

PL_API_BEGIN

enum pl_dither_method {
    // Dither with blue noise. Very high quality, but requires the use of a
    // LUT. Warning: Computing a blue noise texture with a large size can be
    // very slow, however this only needs to be performed once. Even so, using
    // this with a `lut_size` greater than 6 is generally ill-advised. This is
    // the preferred/default dither method.
    PL_DITHER_BLUE_NOISE,

    // Dither with an ordered (bayer) dither matrix, using a LUT. Low quality,
    // and since this also uses a LUT, there's generally no advantage to picking
    // this instead of `PL_DITHER_BLUE_NOISE`. It's mainly there for testing.
    PL_DITHER_ORDERED_LUT,

    // The same as `PL_DITHER_ORDERED_LUT`, but uses fixed function math instead
    // of a LUT. This is faster, but only supports a fixed dither matrix size
    // of 16x16 (equal to a `lut_size` of 4).
    PL_DITHER_ORDERED_FIXED,

    // Dither with white noise. This does not require a LUT and is fairly cheap
    // to compute. Unlike the other modes it doesn't show any repeating
    // patterns either spatially or temporally, but the downside is that this
    // is visually fairly jarring due to the presence of low frequencies in the
    // noise spectrum.
    PL_DITHER_WHITE_NOISE,

    PL_DITHER_METHOD_COUNT,
};

struct pl_dither_params {
    // The source of the dither noise to use.
    enum pl_dither_method method;

    // For the dither methods which require the use of a LUT, this controls
    // the size of the LUT (base 2). If left as NULL, this defaults to 6, which
    // is equivalent to a 64x64 dither matrix. Must not be larger than 8.
    int lut_size;

    // Enables temporal dithering. This reduces the persistence of dithering
    // artifacts by perturbing the dithering matrix per frame.
    // Warning: This can cause nasty aliasing artifacts on some LCD screens.
    bool temporal;

    // Gamma function to use for dither gamma correction. This will only have
    // an effect when dithering to low bit depths (<= 4).
    enum pl_color_transfer transfer;
};

#define PL_DITHER_DEFAULTS                              \
    .method     = PL_DITHER_BLUE_NOISE,                 \
    .lut_size   = 6,                                    \
    /* temporal dithering commonly flickers on LCDs */  \
    .temporal   = false,

#define pl_dither_params(...) (&(struct pl_dither_params) { PL_DITHER_DEFAULTS __VA_ARGS__ })
PL_API extern const struct pl_dither_params pl_dither_default_params;

// Dither the colors to a lower depth, given in bits. This can be used on input
// colors of any precision. Basically, this rounds the colors to only linear
// multiples of the stated bit depth. The average intensity of the result
// will not change (i.e., the dither noise is balanced in both directions).
// If `params` is NULL, it defaults to &pl_dither_default_params.
//
// For the dither methods which require the use of a LUT, `dither_state` must
// be set to a valid pointer. To avoid thrashing the resource, users should
// avoid trying to re-use the same LUT for different dither configurations. If
// passed as NULL, libplacebo will automatically fall back to dither algorithms
// that don't require the use of a LUT.
//
// Warning: This dithering algorithm is not gamma-invariant; so using it for
// very low bit depths (below 4 or so) will noticeably increase the brightness
// of the resulting image. When doing low bit depth dithering for aesthetic
// purposes, it's recommended that the user explicitly (de)linearize the colors
// before and after this algorithm.
PL_API void pl_shader_dither(pl_shader sh, int new_depth,
                             pl_shader_obj *dither_state,
                             const struct pl_dither_params *params);

struct pl_error_diffusion_params {
    // Both the input and output texture must be provided up-front, with the
    // same size. The output texture must be storable, and the input texture
    // must be sampleable.
    pl_tex input_tex;
    pl_tex output_tex;

    // Depth to dither to. Required.
    int new_depth;

    // Error diffusion kernel to use. Optional. If unspecified, defaults to
    // `&pl_error_diffusion_sierra_lite`.
    const struct pl_error_diffusion_kernel *kernel;
};

#define pl_error_diffusion_params(...) (&(struct pl_error_diffusion_params) { __VA_ARGS__ })

// Computes the shared memory requirements for a given error diffusion kernel.
// This can be used to test up-front whether or not error diffusion would be
// supported or not, before having to initialize textures.
PL_API size_t pl_error_diffusion_shmem_req(const struct pl_error_diffusion_kernel *kernel,
                                           int height);

// Apply an error diffusion dithering kernel. This is a much more expensive and
// heavy dithering method, and is not generally recommended for realtime usage
// where performance is critical.
//
// Requires compute shader support. Returns false if dithering fail e.g. as a
// result of shader memory limits being exceeded. The resulting shader must be
// dispatched with a work group count of exactly 1.
PL_API bool pl_shader_error_diffusion(pl_shader sh, const struct pl_error_diffusion_params *params);

PL_API_END

#endif // LIBPLACEBO_SHADERS_DITHERING_H_
