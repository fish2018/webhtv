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

#ifndef LIBPLACEBO_COMMON_H_
#define LIBPLACEBO_COMMON_H_

#include <stdbool.h>

#include <libplacebo/config.h>

PL_API_BEGIN

// Some common utility types. These are overloaded to support 2D, 3D and
// integer/float variants.
typedef struct pl_rect2d {
    int x0, y0;
    int x1, y1;
} pl_rect2d;

typedef struct pl_rect3d {
    int x0, y0, z0;
    int x1, y1, z1;
} pl_rect3d;

typedef struct pl_rect2df {
    float x0, y0;
    float x1, y1;
} pl_rect2df;

typedef struct pl_rect3df {
    float x0, y0, z0;
    float x1, y1, z1;
} pl_rect3df;

// These macros will work for any of the above pl_rect variants (with enough
// dimensions). Careful: double-evaluation hazard
#define pl_rect_w(r) ((r).x1 - (r).x0)
#define pl_rect_h(r) ((r).y1 - (r).y0)
#define pl_rect_d(r) ((r).z1 - (r).z0)

#define pl_rect2d_eq(a, b) \
    ((a).x0 == (b).x0 && (a).x1 == (b).x1 && \
     (a).y0 == (b).y0 && (a).y1 == (b).y1)

#define pl_rect3d_eq(a, b) \
    ((a).x0 == (b).x0 && (a).x1 == (b).x1 && \
     (a).y0 == (b).y0 && (a).y1 == (b).y1 && \
     (a).z0 == (b).z0 && (a).z1 == (b).z1)

// "Normalize" a rectangle: This ensures d1 >= d0 for all dimensions.
PL_API void pl_rect2d_normalize(pl_rect2d *rc);
PL_API void pl_rect3d_normalize(pl_rect3d *rc);

PL_API void pl_rect2df_normalize(pl_rect2df *rc);
PL_API void pl_rect3df_normalize(pl_rect3df *rc);

// Return the rounded form of a rect.
PL_API pl_rect2d pl_rect2df_round(const pl_rect2df *rc);
PL_API pl_rect3d pl_rect3df_round(const pl_rect3df *rc);

// Represents a row-major matrix, i.e. the following matrix
//     [ a11 a12 a13 ]
//     [ a21 a22 a23 ]
//     [ a31 a32 a33 ]
// is represented in C like this:
//   { { a11, a12, a13 },
//     { a21, a22, a23 },
//     { a31, a32, a33 } };
typedef struct pl_matrix3x3 {
    float m[3][3];
} pl_matrix3x3;

PL_API extern const pl_matrix3x3 pl_matrix3x3_identity;

// Applies a matrix to a float vector in-place.
PL_API void pl_matrix3x3_apply(const pl_matrix3x3 *mat, float vec[3]);

// Applies a matrix to a pl_rect3df
PL_API void pl_matrix3x3_apply_rc(const pl_matrix3x3 *mat, pl_rect3df *rc);

// Scales a color matrix by a linear factor.
PL_API void pl_matrix3x3_scale(pl_matrix3x3 *mat, float scale);

// Inverts a matrix. Only use where precision is not that important.
PL_API void pl_matrix3x3_invert(pl_matrix3x3 *mat);

// Composes/multiplies two matrices. Multiples B into A, i.e.
// A := A * B
PL_API void pl_matrix3x3_mul(pl_matrix3x3 *a, const pl_matrix3x3 *b);

// Flipped version of `pl_matrix3x3_mul`.
// B := A * B
PL_API void pl_matrix3x3_rmul(const pl_matrix3x3 *a, pl_matrix3x3 *b);

// Represents an affine transformation, which is basically a 3x3 matrix
// together with a column vector to add onto the output.
typedef struct pl_transform3x3 {
    pl_matrix3x3 mat;
    float c[3];
} pl_transform3x3;

PL_API extern const pl_transform3x3 pl_transform3x3_identity;

// Applies a transform to a float vector in-place.
PL_API void pl_transform3x3_apply(const pl_transform3x3 *t, float vec[3]);

// Applies a transform to a pl_rect3df
PL_API void pl_transform3x3_apply_rc(const pl_transform3x3 *t, pl_rect3df *rc);

// Scales the output of a transform by a linear factor. Since an affine
// transformation is non-linear, this does not commute. If you want to scale
// the *input* of a transform, use pl_matrix3x3_scale on `t.mat`.
PL_API void pl_transform3x3_scale(pl_transform3x3 *t, float scale);

// Inverts a transform. Only use where precision is not that important.
PL_API void pl_transform3x3_invert(pl_transform3x3 *t);

// 2D analog of the above structs. Since these are featured less prominently,
// we omit some of the other helper functions.
typedef struct pl_matrix2x2 {
    float m[2][2];
} pl_matrix2x2;

PL_API extern const pl_matrix2x2 pl_matrix2x2_identity;
PL_API pl_matrix2x2 pl_matrix2x2_rotation(float angle);

PL_API void pl_matrix2x2_apply(const pl_matrix2x2 *mat, float vec[2]);
PL_API void pl_matrix2x2_apply_rc(const pl_matrix2x2 *mat, pl_rect2df *rc);

PL_API void pl_matrix2x2_mul(pl_matrix2x2 *a, const pl_matrix2x2 *b);
PL_API void pl_matrix2x2_rmul(const pl_matrix2x2 *a, pl_matrix2x2 *b);

PL_API void pl_matrix2x2_scale(pl_matrix2x2 *mat, float scale);
PL_API void pl_matrix2x2_invert(pl_matrix2x2 *mat);

typedef struct pl_transform2x2 {
    pl_matrix2x2 mat;
    float c[2];
} pl_transform2x2;

PL_API extern const pl_transform2x2 pl_transform2x2_identity;

PL_API void pl_transform2x2_apply(const pl_transform2x2 *t, float vec[2]);
PL_API void pl_transform2x2_apply_rc(const pl_transform2x2 *t, pl_rect2df *rc);

PL_API void pl_transform2x2_mul(pl_transform2x2 *a, const pl_transform2x2 *b);
PL_API void pl_transform2x2_rmul(const pl_transform2x2 *a, pl_transform2x2 *b);

PL_API void pl_transform2x2_scale(pl_transform2x2 *t, float scale);
PL_API void pl_transform2x2_invert(pl_transform2x2 *t);

// Compute new bounding box of a transformation (as applied to a given rect).
PL_API pl_rect2df pl_transform2x2_bounds(const pl_transform2x2 *t,
                                         const pl_rect2df *rc);

// Helper functions for dealing with aspect ratios and stretched/scaled rects.

// Return the (absolute) aspect ratio (width/height) of a given pl_rect2df.
// This will always be a positive number, even if `rc` is flipped.
PL_API float pl_rect2df_aspect(const pl_rect2df *rc);

// Set the aspect of a `rc` to a given aspect ratio with an extra 'panscan'
// factor choosing the balance between shrinking and growing the `rc` to meet
// this aspect ratio.
//
// Notes:
// - If `panscan` is 0.0, this function will only ever shrink the `rc`.
// - If `panscan` is 1.0, this function will only ever grow the `rc`.
// - If `panscan` is 0.5, this function is area-preserving.
PL_API void pl_rect2df_aspect_set(pl_rect2df *rc, float aspect, float panscan);

// Set one rect's aspect to that of another
#define pl_rect2df_aspect_copy(rc, src, panscan) \
    pl_rect2df_aspect_set((rc), pl_rect2df_aspect(src), (panscan))

// 'Fit' one rect inside another. `rc` will be set to the same size and aspect
// ratio as `src`, but with the size limited to fit inside the original `rc`.
// Like `pl_rect2df_aspect_set`, `panscan` controls the pan&scan factor.
PL_API void pl_rect2df_aspect_fit(pl_rect2df *rc, const pl_rect2df *src, float panscan);

// Scale rect in each direction while keeping it centered.
PL_API void pl_rect2df_stretch(pl_rect2df *rc, float stretch_x, float stretch_y);

// Offset rect by an arbitrary offset factor. If the corresponding dimension
// of a rect is flipped, so too is the applied offset.
PL_API void pl_rect2df_offset(pl_rect2df *rc, float offset_x, float offset_y);

// Scale a rect uniformly in both dimensions.
#define pl_rect2df_zoom(rc, zoom) pl_rect2df_stretch((rc), (zoom), (zoom))

// Rotation in degrees clockwise
typedef int pl_rotation;
enum {
    PL_ROTATION_0   = 0,
    PL_ROTATION_90  = 1,
    PL_ROTATION_180 = 2,
    PL_ROTATION_270 = 3,
    PL_ROTATION_360 = 4, // equivalent to PL_ROTATION_0

    // Note: Values outside the range [0,4) are legal, including negatives.
};

// Constrains to the interval [PL_ROTATION_0, PL_ROTATION_360).
static inline pl_rotation pl_rotation_normalize(pl_rotation rot)
{
    return (rot % PL_ROTATION_360 + PL_ROTATION_360) % PL_ROTATION_360;
}

// Rotates the coordinate system of a `pl_rect2d(f)` in a certain direction.
// For example, calling this with PL_ROTATION_90 will correspond to rotating
// the coordinate system 90° to the right (so the x axis becomes the y axis).
//
// The resulting rect is re-normalized in the same coordinate system.
PL_API void pl_rect2df_rotate(pl_rect2df *rc, pl_rotation rot);

// Returns the aspect ratio in a rotated frame of reference.
static inline float pl_aspect_rotate(float aspect, pl_rotation rot)
{
    return (rot % PL_ROTATION_180) ? 1.0 / aspect : aspect;
}

#define pl_rect2df_aspect_set_rot(rc, aspect, rot, panscan) \
    pl_rect2df_aspect_set((rc), pl_aspect_rotate((aspect), (rot)), (panscan))

#define pl_rect2df_aspect_copy_rot(rc, src, panscan, rot) \
    pl_rect2df_aspect_set_rot((rc), pl_rect2df_aspect(src), (rot), (panscan))

PL_API_END

#endif // LIBPLACEBO_COMMON_H_
