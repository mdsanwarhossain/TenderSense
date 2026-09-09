package com.bracit.tendersense.service;

import com.bracit.tendersense.entity.EligibilityVerdict;
import com.bracit.tendersense.entity.Tender;

import java.util.List;

public interface EligibilityService {

    EligibilityVerdict evaluate(Tender tender);

    List<EligibilityVerdict> evaluateAll(List<Tender> tenders);
}
