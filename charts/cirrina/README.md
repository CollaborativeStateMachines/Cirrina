# Cirrina

The Cirrina Helm chart deploys Cirrina.

## Values

For values, see [values.yaml](values.yaml).

An example `values.yaml` file is as follows:

```yaml
cirrina:
  image:
    tag: unstable

  replicaCount: 2

  appPath: "http://example.com/main.pkl"
  serviceBindingsPath: "http://example.com/services.pkl"
  instantiate: "stateMachineOne,stateMachineTwo"

  natsEventUrl: "nats://nats:4222/"
  natsContextUrl: "nats://nats:4222/"

  extraEnvVars:
    - name: OTEL_METRIC_EXPORT_INTERVAL
      value: "1000"
    - name: OTEL_EXPORTER_OTLP_ENDPOINT
      value: "http://otel-collector:4317/"
```

## Deployment

To deploy Cirrina, use `kubectl`:

```console
helm template prod charts/cirrina
  --namespace default
  -f values.yaml
| kubectl apply -f -
```

Or use another orchestration tool, such
as [Open Cluster Management](https://open-cluster-management.io/):

```console
helm template prod charts/cirrina \
  --namespace default \
  -f values.yaml \
| clusteradm --context="kind-hub" create work prod-cirrina --clusters cluster1 -f -
```