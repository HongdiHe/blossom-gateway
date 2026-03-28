package blossom.project.httpserver.controller;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import blossom.project.client.api.ApiInvoker;
import blossom.project.client.api.ApiProperties;
import blossom.project.client.api.ApiProtocol;
import blossom.project.client.api.ApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@ApiService(serviceId = "backend-http-server", protocol = ApiProtocol.HTTP, patternPath = "/http-server/**")
public class HttpController {
    private static final Logger log = LoggerFactory.getLogger(HttpController.class);

    @Autowired
    private ApiProperties apiProperties;

    @ApiInvoker(path = "/http-server/ping")
    @GetMapping("/http-server/ping")
    public String ping() {
        log.info("{}", apiProperties);
        return "this is application1";
    }

    @ApiInvoker(path = "/http-server/ping2")
    @GetMapping("/http-server/ping2")
    public String ping2() {
        log.info("{}", apiProperties);
        return "this is ping1";
    }
}
