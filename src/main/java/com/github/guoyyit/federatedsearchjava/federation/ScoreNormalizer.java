package com.github.guoyyit.federatedsearchjava.federation;

import com.github.guoyyit.federatedsearchjava.model.SourceHit;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ScoreNormalizer {

    public double weightedScore(SourceHit hit, List<SourceHit> hitsFromSameSource, double weight) {
        double normalized = minMax(hit.rawScore(), hitsFromSameSource);
        return normalized * weight;
    }

    private double minMax(double raw, List<SourceHit> hits) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (SourceHit sourceHit : hits) {
            double score = sourceHit.rawScore();
            if (score < min) {
                min = score;
            }
            if (score > max) {
                max = score;
            }
        }
        if (min == Double.POSITIVE_INFINITY || max <= min) {
            return 1.0;
        }
        return (raw - min) / (max - min);
    }
}
