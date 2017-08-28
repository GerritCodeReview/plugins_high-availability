load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
  maven_jar(
    name = "wiremock",
    artifact = "com.github.tomakehurst:wiremock-standalone:2.5.1",
    sha1 = "9cda1bf1674c8de3a1116bae4d7ce0046a857d30",
  )

  maven_jar(
    name = "mockito",
    artifact = "org.mockito:mockito-core:2.7.21",
    sha1 = "23e9f7bfb9717e849a05b84c29ee3ac723f1a653",
    deps = [
      '@byte-buddy//jar',
      '@objenesis//jar',
    ],
  )

  maven_jar(
    name = "byte-buddy",
    artifact = "net.bytebuddy:byte-buddy:1.6.11",
    sha1 = "8a8f9409e27f1d62c909c7eef2aa7b3a580b4901",
  )

  maven_jar(
    name = "objenesis",
    artifact = "org.objenesis:objenesis:2.5",
    sha1 = "612ecb799912ccf77cba9b3ed8c813da086076e9",
  )

  maven_jar(
    name = "jgroups",
    artifact = "org.jgroups:jgroups:3.6.5.Final",
    sha1 = "fe575fe2d473566ad3f4ace4702ff4bfcf2587a6",
  )
