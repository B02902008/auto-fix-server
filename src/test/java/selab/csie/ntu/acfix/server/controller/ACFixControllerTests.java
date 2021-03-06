package selab.csie.ntu.acfix.server.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import selab.csie.ntu.acfix.server.model.FixingRecord;
import selab.csie.ntu.acfix.server.model.message.ACFixInvokeMessage;
import selab.csie.ntu.acfix.server.service.CmakeACFixService;
import selab.csie.ntu.acfix.server.service.GradleACFixService;
import selab.csie.ntu.acfix.server.service.PipACFixService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

class ACFixControllerTests {

    private MockMvc mockMvc;
    private CmakeACFixService cmakeService;
    private GradleACFixService gradleService;
    private PipACFixService pipService;

    enum URL { url, urlRejected, urlUnmatched  }

    @BeforeEach
    void setup() {
        cmakeService = Mockito.mock(CmakeACFixService.class);
        gradleService = Mockito.mock(GradleACFixService.class);
        pipService = Mockito.mock(PipACFixService.class);
        ACFixController controller = new ACFixController(cmakeService, gradleService, pipService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Nested
    class InvokeACFix {

        private String api;
        private ACFixInvokeMessage msg;
        private ObjectMapper om;

        @BeforeEach
        void setup() {
            api = "/acfix/{tool}";
            msg = new ACFixInvokeMessage("SOCKET_ID", URL.url.toString());
            om = new ObjectMapper();
            BDDMockito.given(gradleService.generateNewRecord(anyString())).willReturn(new FixingRecord());
            BDDMockito.given(cmakeService.generateNewRecord(URL.urlUnmatched.toString())).willThrow(new IllegalArgumentException());
            BDDMockito.given(gradleService.invokeACFix(any(ACFixInvokeMessage.class), any(FixingRecord.class)))
                    .willReturn(1);
            BDDMockito.given(gradleService.invokeACFix(argThat(msg -> msg.getUrl().equals(URL.urlRejected.toString())), any(FixingRecord.class)))
                    .willThrow(new RejectedExecutionException());
        }

        /* Test code 200 for API: Invoke autofix service */
        @Test
        void testAPICodeOk() throws Exception {
            mockMvc.perform(post(api, "gradle")
                    .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(msg)))
                    .andExpect(status().isOk());
        }

        /* Test return value for API: Invoke autofix service */
        @Test
        void testAPICorrectReturn() throws Exception {
            mockMvc.perform(post(api, "gradle")
                    .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(msg)))
                    .andExpect(content().string("1"));
        }

        /* Test code 401 for API: Invoke autofix service (empty url) */
        @Test
        void testAPICodeBadRequestWithEmptyUrl() throws Exception {
            msg.setUrl("");
            ResultActions result = mockMvc.perform(post(api, "gradle")
                    .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(msg)));
            result.andExpect(status().isBadRequest());
            Throwable throwable = result.andReturn().getResolvedException();
            Assertions.assertTrue(throwable != null && throwable.getMessage().contains("non-empty"));
        }

        /* Test code 401 for API: Invoke autofix service (null url) */
        @Test
        void testAPICodeBadRequestWithNullUrl() throws Exception {
            msg.setUrl(null);
            ResultActions result = mockMvc.perform(post(api, "gradle")
                    .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(msg)));
            result.andExpect(status().isBadRequest());
            Throwable throwable = result.andReturn().getResolvedException();
            Assertions.assertTrue(throwable != null && throwable.getMessage().contains("non-empty"));
        }

        /* Test code 401 for API: Invoke autofix service (regex unmatched url) */
        @Test
        void testAPICodeBadRequestWithUnmatchedUrl() throws Exception {
            msg.setUrl(URL.urlUnmatched.toString());
            ResultActions result = mockMvc.perform(post(api, "cmake")
                    .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(msg)));
            result.andExpect(status().isBadRequest());
            Throwable throwable = result.andReturn().getResolvedException();
            Assertions.assertTrue(throwable != null && throwable.getMessage().contains("valid"));
        }

        /* Test code 401 for API: Invoke autofix service (unknown service) */
        @Test
        void testAPICodeBadRequestWithUnknownService() throws Exception {
            ResultActions result = mockMvc.perform(post(api, "UNKNOWN")
                    .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(msg)));
            result.andExpect(status().isBadRequest());
            Throwable throwable = result.andReturn().getResolvedException();
            Assertions.assertTrue(throwable != null && throwable.getMessage().contains("service"));
        }

        /* Test code 405 for API: Invoke autofix service */
        @Test
        void testAPICodeMethodNotAllowed() throws Exception {
            mockMvc.perform(get(api, "gradle")
                    .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(msg)))
                    .andExpect(status().isMethodNotAllowed());
            mockMvc.perform(patch(api, "gradle")
                    .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(msg)))
                    .andExpect(status().isMethodNotAllowed());
            mockMvc.perform(delete(api, "gradle")
                    .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(msg)))
                    .andExpect(status().isMethodNotAllowed());
        }

        /* Test code 503 for API: Invoke autofix service */
        @Test
        void testAPICodeServiceUnavailable() throws Exception {
            msg.setUrl(URL.urlRejected.toString());
            mockMvc.perform(post(api, "gradle")
                    .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(msg)))
                    .andExpect(status().isServiceUnavailable());
        }

    }

    @Nested
    class GetServiceLoading {

        private String api;

        @BeforeEach
        void setup() {
            api = "/acfix/loading/{tool}";
            Map<String, Integer> map = new HashMap<>();
            map.put("core", 10);
            map.put("load", 10);
            BDDMockito.given(pipService.getLoading()).willReturn(map);
        }

        /* Test code 200 for API: Get service loading */
        @Test
        void testAPICodeOk() throws Exception {
            mockMvc.perform(get(api, "pip"))
                    .andExpect(status().isOk());
        }

        /* Test return value for API: Get service loading */
        @Test
        void testAPICorrectReturn() throws Exception {
            mockMvc.perform(get(api, "pip"))
                    .andExpect(jsonPath("$.core", Matchers.any(Integer.class)))
                    .andExpect(jsonPath("$.load", Matchers.any(Integer.class)));
        }

        /* Test code 401 for API: Invoke autofix service (unknown service) */
        @Test
        void testAPICodeBadRequestWithUnknownService() throws Exception {
            mockMvc.perform(get(api, "UNKNOWN"))
                    .andExpect(status().isBadRequest());
        }

        /* Test code 405 for API: Invoke autofix service */
        @Test
        void testAPICodeMethodNotAllowed() throws Exception {
            mockMvc.perform(post(api, "pip"))
                    .andExpect(status().isMethodNotAllowed());
            mockMvc.perform(patch(api, "pip"))
                    .andExpect(status().isMethodNotAllowed());
            mockMvc.perform(delete(api, "pip"))
                    .andExpect(status().isMethodNotAllowed());
        }

    }

}
