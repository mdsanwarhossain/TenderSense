package com.bracit.tendersense.service;

import com.bracit.tendersense.entity.EligibilityVerdict;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.Tender;

import java.util.List;

public interface EligibilityService {

    EligibilityVerdict evaluate(Organisation organisation, Tender tender);

    List<EligibilityVerdict> evaluateAll(Organisation organisation, List<Tender> tenders);
}
