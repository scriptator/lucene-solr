#!/usr/bin/env python3

import os
import requests
import shutil
import subprocess

CORE_NAME = "ircourse"


def check_response(response):
  if response.status_code != 200:
    print("An error ocurred: {}".format(response.text))
    exit(1)

if __name__ == '__main__':
  os.chdir("idea-build")

print("Creating jar file")
subprocess.run(
  "jar -cvf bm25mod.jar "
  "-C solr/solr-core/classes/java/ org/apache/solr/search/similarities/BM25ModSimilarityFactory.class "
  "-C lucene/core/classes/java/ org/apache/lucene/search/similarities/BM25ModSimilarity.class",
  shell=True, check=True)
print()

response = requests.get('http://localhost:8983/solr/admin/cores?action=STATUS&core={}'.format(CORE_NAME))
check_response(response)

instance_dir = response.json()["status"][CORE_NAME]["instanceDir"]
print("Found solr instance dir")
print(instance_dir)

lib_dir = os.path.join(instance_dir, "lib/")
os.makedirs(lib_dir, exist_ok=True)
print("Copy bm25mod.jar -> {}".format(lib_dir))
shutil.copy("bm25mod.jar", lib_dir)

print()
print("Reloading core")
response = requests.post("http://localhost:8983/solr/admin/cores?action=RELOAD&core={}".format(CORE_NAME))
check_response(response)

print()
print("Done.")
