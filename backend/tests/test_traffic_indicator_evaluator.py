from app.services import evaluate_traffic_indicators


def evaluate(capture_mode="controlled", **changes):
    values = {
        "duration_seconds": 20,
        "received_bytes": 20_000,
        "transmitted_bytes": 5_000,
        "received_packets": 20,
        "transmitted_packets": 5,
        "parsed_packets": 8,
        "unparsed_packets": 0,
        "ipv4_packets": 8,
        "ipv6_packets": 0,
        "http_packets": 0,
    }
    return evaluate_traffic_indicators(capture_mode=capture_mode, **(values | changes))


def test_controlled_ipv6_sample_is_described_without_threat_claim():
    result = evaluate(
        parsed_packets=8, ipv4_packets=0, ipv6_packets=8,
    )
    assert [item.code for item in result] == ["controlled_sample", "ipv6_only_sample"]
    assert all(item.severity == "info" for item in result)


def test_unparsed_packets_report_incomplete_visibility():
    result = evaluate(parsed_packets=3, unparsed_packets=2, ipv4_packets=3)
    assert "unparsed_packets" in [item.code for item in result]


def test_http_indicator_only_applies_to_full_capture():
    controlled = evaluate("controlled", parsed_packets=3, ipv4_packets=3, http_packets=1)
    full = evaluate("full", parsed_packets=3, ipv4_packets=3, http_packets=1)
    assert "plaintext_http" not in [item.code for item in controlled]
    assert "plaintext_http" in [item.code for item in full]


def test_full_capture_describes_aggregate_analysis_without_parser_warning():
    result = evaluate("full", parsed_packets=0, ipv4_packets=0)
    codes = [item.code for item in result]
    assert "aggregate_tunnel_analysis" in codes
    assert "no_parsed_packets" not in codes


def test_empty_full_capture_requests_a_new_sample():
    result = evaluate("full", received_packets=0, transmitted_packets=0)
    assert "no_tunnel_traffic" in [item.code for item in result]


def test_outbound_volume_is_a_review_signal_not_a_confirmed_threat():
    result = evaluate(
        "full",
        received_bytes=300_000,
        transmitted_bytes=1_200_000,
    )
    indicator = next(item for item in result if item.code == "outbound_volume_dominant")
    assert indicator.severity == "warning"
    assert "no como amenaza confirmada" in indicator.description


def test_small_outbound_sample_does_not_trigger_volume_signal():
    result = evaluate(
        "full",
        received_bytes=1_000,
        transmitted_bytes=20_000,
    )
    assert "outbound_volume_dominant" not in [item.code for item in result]
