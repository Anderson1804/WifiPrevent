"""Optional paired analysis; never assume study independence or significance."""


def paired_statistics(book, comparisons):
    protocol = book.get("statistics_protocol", {})
    if (book.get("data_kind") != "study" or protocol.get("paired_units_independent") is not True
            or protocol.get("method") != "paired-permutation-v1"):
        return {"status": "not_performed", "reason": "Falta un protocolo de estudio con pares independientes revisados."}
    import numpy as np
    from scipy.stats import permutation_test
    results = {}
    for field in ("detection_difference_percentage_points", "risk_difference_percentage_points"):
        values = np.asarray([r[field] for r in comparisons if r["comparable"] and r.get(field) is not None])
        if len(values) < 5:
            results[field] = {"status": "insufficient_pairs", "pair_count": len(values)}
            continue
        result = permutation_test((values,), np.mean, permutation_type="samples", alternative="two-sided",
                                  n_resamples=9999, batch=200, rng=np.random.default_rng(2026))
        rng = np.random.default_rng(2026)
        means = []
        for _ in range(50):
            means.extend(rng.choice(values, size=(200, len(values)), replace=True).mean(axis=1).tolist())
        lower, upper = np.quantile(means, [.025, .975])
        results[field] = dict(status="computed", pair_count=len(values), mean_difference=float(values.mean()),
            bootstrap_percentile_95_interval=[float(lower), float(upper)], p_value=float(result.pvalue))
    # Holm adjustment across the computed endpoints; no automatic thesis conclusion.
    tested = sorted((name for name in results if results[name]["status"] == "computed"), key=lambda name: results[name]["p_value"])
    prior = 0.
    for index, name in enumerate(tested):
        prior = max(prior, min(1., results[name]["p_value"] * (len(tested)-index)))
        results[name]["holm_adjusted_p_value"] = prior
    return dict(status="review_required", method="paired-permutation-v1", indicators=results,
                limitation="La independencia, intercambiabilidad de las condiciones y el tamaño muestral requieren justificación metodológica.")
