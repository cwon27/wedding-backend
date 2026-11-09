package com.wedding.guestbook.device;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.wedding.guestbook.config.AppProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 로그인 없이 "기기"를 구분하기 위한 쿠키.
 * 쿠키가 없으면 새 기기 ID 를 발급하고, 요청 속성에 담아 컨트롤러가 꺼내 쓰게 한다.
 * 캐릭터 1개 / 재생성 1회 제한은 이 ID 를 기준으로 서버에서 판단한다.
 */
@Component
@RequiredArgsConstructor
public class DeviceIdFilter extends OncePerRequestFilter {

	public static final String ATTRIBUTE = DeviceIdFilter.class.getName() + ".deviceId";

	private final AppProperties props;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		AppProperties.Device device = props.device();
		String deviceId = readCookie(request, device.cookieName())
				.filter(DeviceIdFilter::isValid)
				.orElseGet(() -> {
					String issued = UUID.randomUUID().toString();
					ResponseCookie cookie = ResponseCookie.from(device.cookieName(), issued)
							.httpOnly(true)
							.secure(device.secure())
							.sameSite("Lax")
							.path("/")
							.maxAge(device.maxAge())
							.build();
					response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
					return issued;
				});
		request.setAttribute(ATTRIBUTE, deviceId);
		chain.doFilter(request, response);
	}

	private static Optional<String> readCookie(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		return Arrays.stream(cookies).filter(c -> name.equals(c.getName())).map(Cookie::getValue).findFirst();
	}

	private static boolean isValid(String value) {
		try {
			UUID.fromString(value);
			return true;
		}
		catch (IllegalArgumentException e) {
			return false;
		}
	}
}
