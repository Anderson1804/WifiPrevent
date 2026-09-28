from collections.abc import Iterable


def recommend_preventive_actions(
        risk_level: str | None,
        security_type: str | None,
        captive_portal: bool | None,
        sample_quality: str,
        indicator_codes: Iterable[str],
) -> tuple[str, ...]:
    """Return practical advice from metadata already evaluated by the backend."""
    codes = set(indicator_codes)
    recommendations: list[str] = []

    if sample_quality == "insufficient":
        recommendations.append(
            "Repite el análisis durante al menos 30 segundos mientras navegas para obtener una muestra más representativa."
        )
    elif sample_quality == "limited":
        recommendations.append(
            "Realiza una sesión más larga antes de tomar decisiones sobre esta red."
        )

    normalized_security = (security_type or "").upper()
    if normalized_security in {"OPEN", "NONE", "OWE_TRANSITION"}:
        recommendations.append(
            "Evita iniciar sesión, realizar pagos o enviar información sensible mientras uses esta red abierta."
        )
        recommendations.append(
            "Prefiere tus datos móviles o una VPN de confianza si necesitas transmitir información sensible."
        )

    if captive_portal is True:
        recommendations.append(
            "Verifica que el portal de acceso pertenezca al establecimiento antes de ingresar datos."
        )
    if "plaintext_http" in codes:
        recommendations.append(
            "No introduzcas contraseñas ni datos personales en páginas que no muestren HTTPS."
        )
    if "outbound_volume_dominant" in codes:
        recommendations.append(
            "Revisa si había copias de seguridad, cargas de archivos o aplicaciones sincronizando datos durante la sesión."
        )
    if "no_tunnel_traffic" in codes:
        recommendations.append(
            "Repite la captura y genera tráfico navegando por varias páginas o usando una aplicación con Internet."
        )

    if not recommendations and risk_level == "low":
        recommendations.append(
            "Mantén el sistema actualizado y confirma que los sitios utilizados conserven HTTPS."
        )
    elif not recommendations:
        recommendations.append(
            "Interpreta el resultado como una orientación preliminar y repite el análisis si cambian las condiciones de la red."
        )

    return tuple(dict.fromkeys(recommendations))
