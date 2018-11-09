1. Install [nghttp](https://github.com/http2/http2-spec/wiki/Tools)
2. Run commands:
```bash
$ git clone https://github.com/dapengzhang0/servlettest.git
$ cd servlettest
$ ./gradlew installDist
$ i="0"; while (build/install/servlettest/bin/async_embedded); do  i=$[$i+1]; done; echo "failed after running ${i} times"
```
