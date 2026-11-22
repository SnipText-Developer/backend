provider "aws" {
  region = var.region

  default_tags {
    tags = {
      project      = "sniptext"
      env          = "prod"
      "managed-by" = "terraform"
    }
  }
}
