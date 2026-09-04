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

#ifndef LIBPLACEBO_SWAPCHAIN_H_
#define LIBPLACEBO_SWAPCHAIN_H_

#include <libplacebo/common.h>
#include <libplacebo/colorspace.h>
#include <libplacebo/gpu.h>

PL_API_BEGIN

// This abstraction represents a low-level interface to visible surfaces
// exposed by a graphics API (and accompanying GPU instance), allowing users to
// directly present frames to the screen (or window, typically). This is a
// sister API to gpu.h and follows the same convention w.r.t undefined behavior.
//
// Thread-safety: Safe
typedef const struct pl_swapchain_t {
    pl_log log;
    pl_gpu gpu;
} *pl_swapchain;

// Destroys this swapchain. May be used at any time, and may block until the
// completion of all outstanding rendering commands. The swapchain and any
// resources retrieved from it must not be used afterwards.
PL_API void pl_swapchain_destroy(pl_swapchain *sw);

// Returns the approximate current swapchain latency in vsyncs, or 0 if
// unknown. A latency of 1 means that `submit_frame` followed by `swap_buffers`
// will block until the just-submitted frame has finished rendering. Typical
// values are 2 or 3, which enable better pipelining by allowing the GPU to be
// processing one or two frames at the same time as the user is preparing the
// next for submission.
PL_API int pl_swapchain_latency(pl_swapchain sw);

// Update/query the swapchain size. This function performs both roles: it tries
// setting the swapchain size to the values requested by the user, and returns
// in the same variables what width/height the swapchain was actually set to -
// which may be (substantially) different from the values requested by the
// user. A value of 0 means "unknown/none" (in which case, libplacebo won't try
// updating the size - it will simply return the current state of the
// swapchain). It's also possible for libplacebo to return values of 0, such as
// in the case that the swapchain doesn't exist yet.
//
// Returns false on significant errors (e.g. dead surface). This function can
// effectively be used to probe if creating a swapchain works.
PL_API bool pl_swapchain_resize(pl_swapchain sw, int *width, int *height);

// Backwards compatibility
#define pl_swapchain_colors pl_color_space

// Inform the swapchain about the input color space. This API deliberately
// provides no feedback, because the swapchain can internally decide what to do
// with this information, including ignoring it entirely, or applying it
// asynchronously. Users must still base their rendering on the value of
// `pl_swapchain_frame.color_space`.
//
// Note: Calling this function a second time completely overrides any
// previously specified hint. So calling this on {0} or NULL resets the
// swapchain back to its initial/preferred colorspace.
//
// Note: If `csp->transfer` is a HDR transfer curve but HDR metadata is left
// unspecified, the HDR metadata defaults to `pl_hdr_metadata_hdr10`.
// Conversely, if the HDR metadata is non-empty but `csp->transfer` is left as
// PL_COLOR_TRC_UNKNOWN, then it instead defaults to PL_COLOR_TRC_PQ.
PL_API void pl_swapchain_colorspace_hint(pl_swapchain sw, const struct pl_color_space *csp);

// The struct used to hold the results of `pl_swapchain_start_frame`
struct pl_swapchain_frame {
    // A texture representing the framebuffer users should use for rendering.
    // It's guaranteed that `fbo->params.renderable` and `fbo->params.blit_dst`
    // will be true, but no other guarantees are made - not even that
    // `fbo->params.format` is a real format.
    pl_tex fbo;

    // If true, the user should assume that this framebuffer will be flipped
    // as a result of presenting it on-screen. If false, nothing special needs
    // to be done - but if true, users should flip the coordinate system of
    // the `pl_pass` that is rendering to this framebuffer.
    //
    // Note: Normally, libplacebo follows the convention that (0,0) represents
    // the top left of the image/screen. So when flipped is true, this means
    // (0,0) on this framebuffer gets displayed as the bottom left of the image.
    bool flipped;

    // Indicates the color representation this framebuffer will be interpreted
    // as by the host system / compositor / display, including the bit depth
    // and alpha handling (where available).
    struct pl_color_repr color_repr;
    struct pl_color_space color_space;
};

// Retrieve a new frame from the swapchain. Returns whether successful. It's
// worth noting that this function can fail sporadically for benign reasons,
// for example the window being invisible or inaccessible. This function may
// block until an image is available, which may be the case if the GPU is
// rendering frames significantly faster than the display can output them. It
// may also be non-blocking, so users shouldn't rely on this call alone in
// order to meter rendering speed. (Specifics depend on the underlying graphics
// API)
PL_API bool pl_swapchain_start_frame(pl_swapchain sw, struct pl_swapchain_frame *out_frame);

// Submits the previously started frame. Non-blocking. This must be issued in
// lockstep with pl_swapchain_start_frame - there is no way to start multiple
// frames and submit them out-of-order. The frames submitted this way will
// generally be made visible in a first-in first-out fashion, although
// specifics depend on the mechanism used to create the pl_swapchain. (See the
// platform-specific APIs for more info).
//
// Returns whether successful. This should normally never fail, unless the
// GPU/surface has been lost or some other critical error has occurred. The
// "started" frame is consumed even in the event of failure.
//
// Note that `start_frame` and `submit_frame` form a lock pair, i.e. trying to
// call e.g. `pl_swapchain_resize` from another thread will block until
// `pl_swapchain_submit_frame` is finished.
PL_API bool pl_swapchain_submit_frame(pl_swapchain sw);

// Performs a "buffer swap", or some generalization of the concept. In layman's
// terms, this blocks until the execution of the Nth previously submitted frame
// has been "made complete" in some sense. (The N derives from the swapchain's
// built-in latency. See `pl_swapchain_latency` for more information).
//
// Users should include this call in their rendering loops in order to make
// sure they aren't submitting rendering commands faster than the GPU can
// process them, which would potentially lead to a queue overrun or exhaust
// memory.
//
// An example loop might look like this:
//
//     while (rendering) {
//         struct pl_swapchain_frame frame;
//         bool ok = pl_swapchain_start_frame(swapchain, &frame);
//         if (!ok) {
//             /* wait some time, or decide to stop rendering */
//             continue;
//         }
//
//         /* do some rendering with frame.fbo */
//
//         ok = pl_swapchain_submit_frame(swapchain);
//         if (!ok)
//             break;
//
//         pl_swapchain_swap_buffers(swapchain);
//     }
//
// The duration this function blocks for, if at all, may be very inconsistent
// and should not be used as an authoritative source of vsync timing
// information without sufficient smoothing/filtering (and if so, the time that
// `start_frame` blocked for should also be included).
PL_API void pl_swapchain_swap_buffers(pl_swapchain sw);

PL_API_END

#endif // LIBPLACEBO_SWAPCHAIN_H_
