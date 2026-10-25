package com.ftcrm.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI ftcrmOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("澄远外贸 CRM")
                        .version("0.1.0")
                        .description("阶段①认证与角色，阶段③联系人保险库。M2 消息接口只收客户编号；unconfigured 返回 501，mock/实发返回 202，不返回联系方式明文。"))
                .addSecurityItem(new SecurityRequirement().addList("bearer"))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
