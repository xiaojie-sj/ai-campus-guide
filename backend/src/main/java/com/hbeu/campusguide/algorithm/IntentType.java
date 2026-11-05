package com.hbeu.campusguide.algorithm;

/**
 * Four core intents plus two control states. CLARIFY and UNKNOWN are not
 * included in the four-class intent macro metric.
 */
public enum IntentType {
    BUILDING_INTRO,
    ROUTE,
    INFO,
    SCENE_CONTROL,
    CLARIFY,
    UNKNOWN
}
