package com.scriptum.backend.api.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The two collaborators every {@link WebLayerTest} slice needs.
 *
 * <p>Kept as a base class rather than repeated fields so the slices stay focused
 * on the requests they describe.
 */
abstract class WebLayerTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;
}
