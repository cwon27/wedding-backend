package com.wedding.guestbook.device;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class DeviceIdArgumentResolver implements HandlerMethodArgumentResolver, WebMvcConfigurer {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(DeviceId.class) && parameter.getParameterType() == String.class;
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		Object deviceId = webRequest.getAttribute(DeviceIdFilter.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
		if (deviceId == null) {
			throw new IllegalStateException("DeviceIdFilter 가 적용되지 않은 경로입니다: /api/** 에서만 사용하세요");
		}
		return deviceId;
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(this);
	}
}
