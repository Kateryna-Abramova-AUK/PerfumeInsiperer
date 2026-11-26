terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

variable "aws_region" {
  type        = string
  description = "Region for the catalog bucket and publisher policy."
}

variable "catalog_bucket_name" {
  type        = string
  description = "Globally unique S3 bucket name."
}

provider "aws" {
  region = var.aws_region
}

resource "aws_s3_bucket" "catalog" {
  bucket = var.catalog_bucket_name
}

resource "aws_s3_bucket_ownership_controls" "catalog" {
  bucket = aws_s3_bucket.catalog.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "catalog" {
  bucket                  = aws_s3_bucket.catalog.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "catalog" {
  bucket = aws_s3_bucket.catalog.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "catalog" {
  bucket = aws_s3_bucket.catalog.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_policy" "catalog" {
  bucket     = aws_s3_bucket.catalog.id
  depends_on = [aws_s3_bucket_public_access_block.catalog]

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource  = [aws_s3_bucket.catalog.arn, "${aws_s3_bucket.catalog.arn}/*"]
      Condition = { Bool = { "aws:SecureTransport" = "false" } }
    }]
  })
}

resource "aws_iam_policy" "publisher" {
  name_prefix = "scent-mood-catalog-publisher-"
  description = "Publish new versions of the Scent Mood catalog artifact."
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:PutObject"]
      Resource = "${aws_s3_bucket.catalog.arn}/catalog/latest.json"
    }]
  })
}

output "catalog_bucket_name" {
  value = aws_s3_bucket.catalog.id
}

output "publisher_policy_arn" {
  value = aws_iam_policy.publisher.arn
}
