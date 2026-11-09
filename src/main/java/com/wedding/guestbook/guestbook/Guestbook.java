package com.wedding.guestbook.guestbook;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Guestbook {

	public enum Status {
		OPEN, READ_ONLY
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String groomName;

	private String brideName;

	private OffsetDateTime weddingAt;

	@Enumerated(EnumType.STRING)
	private Status status;

	private int maxCharacters;

	@Column(insertable = false, updatable = false)
	private OffsetDateTime createdAt;
}
