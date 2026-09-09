package com.bracit.tendersense.dto;

import com.bracit.tendersense.entity.enums.Sector;

import java.util.List;

public record SignupRequest(String companyName,
                            String email,
                            String password,
                            List<Sector> sectors) {}
