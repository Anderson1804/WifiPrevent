import json

import pytest

import local_services


def test_phone_access_config_accepts_windows_utf8_bom(tmp_path, monkeypatch):
    config = tmp_path / "phone-access.json"
    config.write_text(
        json.dumps({"phone_ip": "192.168.18.219"}),
        encoding="utf-8-sig",
    )
    monkeypatch.setattr(local_services, "PHONE_ACCESS_FILE", config)

    assert local_services.configured_phone_ip() == "192.168.18.219"


@pytest.mark.parametrize(
    "content",
    ["not-json", '{"phone_ip":"8.8.8.8"}', '{"phone_ip":"::1"}'],
)
def test_phone_access_config_rejects_invalid_or_non_private_ip(
        tmp_path, monkeypatch, content,
):
    config = tmp_path / "phone-access.json"
    config.write_text(content, encoding="utf-8")
    monkeypatch.setattr(local_services, "PHONE_ACCESS_FILE", config)

    with pytest.raises(RuntimeError):
        local_services.configured_phone_ip()
