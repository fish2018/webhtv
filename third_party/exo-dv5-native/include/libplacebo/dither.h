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

#ifndef LIBPLACEBO_DITHER_H_
#define LIBPLACEBO_DITHER_H_

#include <libplacebo/common.h>

PL_API_BEGIN

// Generates a deterministic NxN bayer (ordered) dither matrix, storing the
// result in `data`. `size` must be a power of two. The resulting matrix will
// be roughly uniformly distributed within the range [0,1).
PL_API void pl_generate_bayer_matrix(float *data, int size);

// Generates a random NxN blue noise texture. storing the result in `data`.
// `size` must be a positive power of two no larger than 256. The resulting
// texture will be roughly uniformly distributed within the range [0,1).
//
// Note: This function is very, *very* slow for large sizes. Generating a
// dither matrix with size 256 can take several seconds on a modern processor.
PL_API void pl_generate_blue_noise(float *data, int size);

// Defines the border of all error diffusion kernels
#define PL_EDF_MIN_DX (-2)
#define PL_EDF_MAX_DX  (2)
#define PL_EDF_MAX_DY  (2)

struct pl_error_diffusion_kernel {
    const char *name; // Short and concise identifier
    const char *description; // Longer / friendly name

    // The minimum value such that a (y, x) -> (y, x + y * shift) mapping will
    // make all error pushing operations affect next column (and after it)
    // only.
    //
    // Higher shift values are significantly more computationally intensive.
    int shift;

    // The diffusion factor for (y, x) is pattern[y][x - PL_EDF_MIN_DX] / divisor.
    int pattern[PL_EDF_MAX_DY + 1][PL_EDF_MAX_DX - PL_EDF_MIN_DX + 1];
    int divisor;
};

// Algorithms with shift=1:
PL_API extern const struct pl_error_diffusion_kernel pl_error_diffusion_simple;
PL_API extern const struct pl_error_diffusion_kernel pl_error_diffusion_false_fs;
// Algorithms with shift=2:
PL_API extern const struct pl_error_diffusion_kernel pl_error_diffusion_sierra_lite;
PL_API extern const struct pl_error_diffusion_kernel pl_error_diffusion_floyd_steinberg;
PL_API extern const struct pl_error_diffusion_kernel pl_error_diffusion_atkinson;
// Algorithms with shift=3, probably too heavy for low end GPUs:
PL_API extern const struct pl_error_diffusion_kernel pl_error_diffusion_jarvis_judice_ninke;
PL_API extern const struct pl_error_diffusion_kernel pl_error_diffusion_stucki;
PL_API extern const struct pl_error_diffusion_kernel pl_error_diffusion_burkes;
PL_API extern const struct pl_error_diffusion_kernel pl_error_diffusion_sierra2;
PL_API extern const struct pl_error_diffusion_kernel pl_error_diffusion_sierra3;

// A list of built-in error diffusion kernels, terminated by NULL
PL_API extern const struct pl_error_diffusion_kernel * const pl_error_diffusion_kernels[];
PL_API extern const int pl_num_error_diffusion_kernels; // excluding trailing NULL

// Find the error diffusion kernel with the given name, or NULL on failure.
PL_API const struct pl_error_diffusion_kernel *pl_find_error_diffusion_kernel(const char *name);

PL_API_END

#endif // LIBPLACEBO_DITHER_H_
