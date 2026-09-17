package com.fongmi.android.tv.service;

import com.fongmi.android.tv.bean.AiConfig;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

import static org.junit.Assert.assertEquals;

public class AiGroupRuleTimeoutTest {

    @Test
    public void aiGroupRuleRequestsAllowFiveMinutesAtEveryTimeoutLayer() throws Exception {
        AiGroupRuleService service = new AiGroupRuleService(new AiConfig());
        Method clientMethod = AiGroupRuleService.class.getDeclaredMethod("client", long.class);
        clientMethod.setAccessible(true);
        OkHttpClient client = (OkHttpClient) clientMethod.invoke(service, TimeUnit.MINUTES.toMillis(5));

        assertEquals(15, integerConstant("CONNECT_TIMEOUT_SECONDS"));
        assertEquals(5 * 60, integerConstant("READ_TIMEOUT_SECONDS"));
        assertEquals(5 * 60, integerConstant("CALL_TIMEOUT_SECONDS"));
        assertEquals(5 * 60, integerConstant("MAX_ANALYSIS_SECONDS"));
        assertEquals(TimeUnit.SECONDS.toMillis(15), client.connectTimeoutMillis());
        assertEquals(TimeUnit.MINUTES.toMillis(5), client.readTimeoutMillis());
        assertEquals(TimeUnit.MINUTES.toMillis(5), client.callTimeoutMillis());
    }

    private static int integerConstant(String name) throws Exception {
        Field field = AiGroupRuleService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(null);
    }
}
