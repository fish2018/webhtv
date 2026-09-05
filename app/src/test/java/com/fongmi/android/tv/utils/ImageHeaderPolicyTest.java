package com.fongmi.android.tv.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ImageHeaderPolicyTest {

    @Test
    public void doubanImageReferer_returnsDefaultForDoubanImageHosts() {
        assertEquals("https://movie.douban.com", ImageHeaderPolicy.doubanImageReferer("https://img1.doubanio.com/view/photo/s_ratio_poster/public/p451926968.jpg", false));
        assertEquals("https://movie.douban.com", ImageHeaderPolicy.doubanImageReferer("https://img9.doubanio.com/view/photo/m_ratio_poster/public/p2576977981.jpg", false));
        assertEquals("https://movie.douban.com", ImageHeaderPolicy.doubanImageReferer("https://img12.doubanio.com/view/photo/photo/public/p1.jpg", false));
    }

    @Test
    public void doubanImageReferer_ignoresNonDoubanImageHosts() {
        assertEquals("", ImageHeaderPolicy.doubanImageReferer("https://img1.example.com/view/photo/p1.jpg", false));
        assertEquals("", ImageHeaderPolicy.doubanImageReferer("https://movie.douban.com/subject/1291843/", false));
        assertEquals("", ImageHeaderPolicy.doubanImageReferer("https://img.doubanio.com/view/photo/p1.jpg", false));
    }

    @Test
    public void doubanImageReferer_preservesExistingReferer() {
        assertEquals("", ImageHeaderPolicy.doubanImageReferer("https://img1.doubanio.com/view/photo/p1.jpg", true));
    }

    @Test
    public void isReferer_matchesCaseInsensitively() {
        assertTrue(ImageHeaderPolicy.isReferer("Referer"));
        assertTrue(ImageHeaderPolicy.isReferer("referer"));
        assertFalse(ImageHeaderPolicy.isReferer("User-Agent"));
    }
}
