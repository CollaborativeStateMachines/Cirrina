# Cirrina Examples

This directory contains a collection of examples.

To run an example on multiple isolated resources, we recommend
using [Vagrant](https://developer.hashicorp.com/vagrant).

To execute the [helloWorld](tutorial/helloWorld.pkl) example, run:

```
./cirrina \
  RUN=one,two \
  MAIN_URI=file:///examples/tutorial/helloWorld.pkl \
  ETCD_CONTEXT_URL=http://localhost:2379
```