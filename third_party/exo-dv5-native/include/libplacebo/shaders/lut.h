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

#ifndef LIBPLACEBO_SHADERS_LUT_H_
#define LIBPLACEBO_SHADERS_LUT_H_

// Shaders for loading and applying arbitrary custom 1D/3DLUTs

#include <libplacebo/colorspace.h>
#include <libplacebo/shaders.h>

PL_API_BEGIN

// Struct defining custom LUTs
//
// Note: Users may freely create their own instances of this struct, there is
// nothing particularly special about `pl_lut_parse_cube`.
struct pl_custom_lut {
    // Some unique signature identifying this LUT, needed to detect state
    // changes (for cache invalidation). This should ideally be a hash of the
    // file contents. (Which is what `pl_lut_parse_*` will set it to.)
    uint64_t signature;

    // Size of each dimension, in the order R, G, B. For 1D LUTs, only the R
    // dimension should be specified (the others left as 0).
    int size[3];

    // Raw LUT data itself, in properly scaled floating point format. For 3D
    // LUTs, the innermost dimension is the first dimension (R), and the
    // outermost dimension is the last dimension (B). Individual color samples
    // are in the order R, G, B.
    const float *data;

    // Extra input/output shaper matrices. Ignored if equal to {0}. This is
    // mostly useful for 1D LUTs, since 3D LUTs can bake the shaper matrix into
    // the LUT itself - but it can still help optimize LUT precision.
    pl_matrix3x3 shaper_in, shaper_out;

    // Nominal metadata for the input/output of a LUT. Left as {0} if unknown.
    // Note: This is purely informative, `pl_shader_custom_lut` ignores it.
    struct pl_color_repr repr_in, repr_out;
    struct pl_color_space color_in, color_out;
};

// Parse a 3DLUT in .cube format. Returns NULL if the file fails parsing.
PL_API struct pl_custom_lut *pl_lut_parse_cube(pl_log log, const char *str, size_t str_len);

// Frees a LUT created by `pl_lut_parse_*`.
PL_API void pl_lut_free(struct pl_custom_lut **lut);

// Apply a `pl_custom_lut`. The user is responsible for ensuring colors going
// into the LUT are in the expected format as informed by the LUT metadata.
//
// `lut_state` must be a pointer to a NULL-initialized shader state object that
// will be used to encapsulate any required GPU state.
//
// Note: `lut` does not have to be allocated by `pl_lut_parse_*`. It can be a
// struct filled out by the user.
PL_API void pl_shader_custom_lut(pl_shader sh, const struct pl_custom_lut *lut,
                                 pl_shader_obj *lut_state);

PL_API_END

#endif // LIBPLACEBO_SHADERS_LUT_H_
