# Docker Registry Configuration
REGISTRY="${ODC_REGISTRY:-reg.actiontech.com}"
REPOSITORY="${ODC_REPOSITORY:-actiontech-dev/odc/build-env}"
TAG="${ODC_TAG:-1.0}"
IMAGE_NAME="${ODC_BUILD_IMAGE:-${REGISTRY}/${REPOSITORY}:${TAG}}"

# Container Name
CONTAINER_NAME=${GO_PIPELINE_NAME:-odc-builder}
