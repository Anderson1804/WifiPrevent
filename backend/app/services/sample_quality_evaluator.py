from typing import Literal


SampleQuality = Literal["insufficient", "limited", "adequate"]


def evaluate_sample_quality(
        duration_seconds: int,
        received_packets: int,
        transmitted_packets: int,
) -> SampleQuality:
    """Describe sample coverage without changing the calculated risk."""
    total_packets = received_packets + transmitted_packets
    if duration_seconds < 5 or total_packets < 10:
        return "insufficient"
    if duration_seconds < 30 or total_packets < 100:
        return "limited"
    return "adequate"
