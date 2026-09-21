from app.services import evaluate_traffic_indicators


def test_controlled_ipv6_sample_is_described_without_threat_claim():
    result = evaluate_traffic_indicators("controlled", 8, 0, 0, 8, 0)
    assert [item.code for item in result] == ["controlled_sample", "ipv6_only_sample"]
    assert all(item.severity == "info" for item in result)


def test_unparsed_packets_report_incomplete_visibility():
    result = evaluate_traffic_indicators("controlled", 3, 2, 3, 0, 0)
    assert "unparsed_packets" in [item.code for item in result]


def test_http_indicator_only_applies_to_full_capture():
    controlled = evaluate_traffic_indicators("controlled", 3, 0, 3, 0, 1)
    full = evaluate_traffic_indicators("full", 3, 0, 3, 0, 1)
    assert "plaintext_http" not in [item.code for item in controlled]
    assert "plaintext_http" in [item.code for item in full]
