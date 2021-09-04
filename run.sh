build -t --rm jdk-11-ssh .
docker run -dit -p 10080:80 -p 18080:8080 -p 10022:22 --name "jdk11-development-1" -h "base-tiny-tim.io" -v "D:\SharedDist":/shared-dist jdk-11-ssh

