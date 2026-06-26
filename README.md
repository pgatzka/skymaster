# SkyMaster

## Development

### Setup git hooks

```shell
git config core.hooksPath .githooks
```


# repeatable:
#   Checkout
#   Setup Java
#   Setup Maven

# Jobs
#   check-formatting
#   test
#     check-formatting
#   build
#     test
#   sonar
#     test
#   package
#     build
#   publish
#     package
#   deploy [main]
#     package
