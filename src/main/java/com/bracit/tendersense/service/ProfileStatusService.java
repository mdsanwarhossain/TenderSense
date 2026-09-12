package com.bracit.tendersense.service;

import com.bracit.tendersense.dto.ProfileStaleness;
import com.bracit.tendersense.entity.Organisation;

/** Whether a company's stored scores still reflect its stored profile. */
public interface ProfileStatusService {

    ProfileStaleness staleness(Organisation organisation);
}
