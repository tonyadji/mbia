# Route 53. The zone lehnade.com was created with the domain (plan §2.2): read only, never managed here.
data "aws_route53_zone" "lehnade" {
  name = "lehnade.com"
}

resource "aws_route53_record" "frontend" {
  for_each = toset(["A", "AAAA"])

  zone_id = data.aws_route53_zone.lehnade.zone_id
  name    = local.frontend_domain
  type    = each.key

  alias {
    name                   = aws_cloudfront_distribution.frontend.domain_name
    zone_id                = aws_cloudfront_distribution.frontend.hosted_zone_id
    evaluate_target_health = false
  }
}

# Caddy on the VM; Let's Encrypt checks these names over HTTP (port 80).
resource "aws_route53_record" "vm" {
  for_each = toset([local.api_domain, local.auth_domain])

  zone_id = data.aws_route53_zone.lehnade.zone_id
  name    = each.key
  type    = "A"
  ttl     = 300
  records = [aws_eip.vm.public_ip]
}

resource "aws_route53_record" "frontend_certificate" {
  for_each = {
    for option in aws_acm_certificate.frontend.domain_validation_options : option.domain_name => option
  }

  zone_id = data.aws_route53_zone.lehnade.zone_id
  name    = each.value.resource_record_name
  type    = each.value.resource_record_type
  ttl     = 300
  records = [each.value.resource_record_value]
}
