import pytest

from app.services.sample_quality_evaluator import evaluate_sample_quality


@pytest.mark.parametrize(
    ("duration", "received", "transmitted", "expected"),
    [
        (4, 100, 100, "insufficient"),
        (30, 5, 4, "insufficient"),
        (5, 5, 5, "limited"),
        (29, 100, 100, "limited"),
        (30, 50, 49, "limited"),
        (30, 50, 50, "adequate"),
    ],
)
def test_evaluates_sample_coverage(duration, received, transmitted, expected):
    assert evaluate_sample_quality(duration, received, transmitted) == expected
