from OpenSSL import crypto

def convert_pem_to_jks(pem_path, jks_path, jks_password):
    """
    Convert a .pem file to a Java KeyStore (.jks) file.

    :param pem_path: Path to the .pem file.
    :param jks_path: Path to save the .jks file.
    :param jks_password: Password for the .jks file.
    """
    # Load PEM certificate
    with open(pem_path, 'rt') as file:
        pem_data = file.read()
    certificate = crypto.load_certificate(crypto.FILETYPE_PEM, pem_data)

    # Create a new JKS keystore
    from jks import KeyStore
    keystore = KeyStore.new('jks', [])

    # Convert PEM to PKCS12 (intermediate step)
    p12 = crypto.PKCS12()
    p12.set_certificate(certificate)
    p12_data = p12.export()

    # Load the PKCS12 data into the keystore
    keystore.entries['converted_entry'] = KeyStore.loads_pkcs12(p12_data, jks_password)

    # Save the JKS keystore to a file
    with open(jks_path, 'wb') as file:
        keystore.save(file, jks_password)

convert_pem_to_jks('path/to/certificate.pem', 'path/to/keystore.jks', 'your_jks_password')

==========================================
import zipfile

# Replace 'your_zip_file.zip' with your zip file's name
zip_file_name = 'your_zip_file.zip'

with zipfile.ZipFile(zip_file_name, 'r') as zip:
    # Iterate over each file in the zip file
    for file_name in zip.namelist():
        print(f"Reading {file_name}...")
        with zip.open(file_name) as f:
            # Reading each file line by line
            for line in f:
                print(line.decode().strip())  # Decoding from bytes to string and stripping newlines

===========================================
import zipfile

def read_lines_from_zip(zip_file_name):
    with zipfile.ZipFile(zip_file_name, 'r') as zip_file:
        for file_name in zip_file.namelist():
            # Open each file in the zip
            with zip_file.open(file_name) as file:
                # Read and yield each line
                for line in file:
                    yield line.decode().strip()

# Usage example
zip_file_name = 'your_zip_file.zip'
for line in read_lines_from_zip(zip_file_name):
    print(line)
    # You can break or return if you want to stop after a certain condition
===============================================

import requests
import zipfile
import tempfile
import os

def download_jar(url):
    response = requests.get(url)
    if response.status_code == 200:
        jar_file = os.path.join(tempfile.gettempdir(), 'downloaded.jar')
        with open(jar_file, 'wb') as file:
            file.write(response.content)
        return jar_file
    else:
        raise Exception(f"Failed to download JAR file. Status code: {response.status_code}")

def extract_zip_from_jar(jar_file, zip_file_name):
    with zipfile.ZipFile(jar_file, 'r') as jar:
        zip_file_path = os.path.join(tempfile.gettempdir(), zip_file_name)
        jar.extract(zip_file_name, tempfile.gettempdir())
        return zip_file_path

# Example usage
url = 'YOUR_MAVEN_REPOSITORY_URL'
zip_file_name = 'YOUR_ZIP_FILE_NAME_INSIDE_JAR'

try:
    # Download JAR file
    jar_file_path = download_jar(url)

    # Extract ZIP file from JAR
    zip_file_path = extract_zip_from_jar(jar_file_path, zip_file_name)

    print(f"ZIP file extracted to: {zip_file_path}")

    # Optionally, delete the JAR file if not needed
    # os.remove(jar_file_path)

except Exception as e:
    print(str(e))


=========================================

import zipfile
import os

def extract_directory_from_zip(zip_file_path, directory_to_extract, extract_to_path):
    with zipfile.ZipFile(zip_file_path, 'r') as zip_ref:
        for member in zip_ref.namelist():
            # Check if the file is within the directory to extract
            if member.startswith(directory_to_extract):
                # Extract the file
                zip_ref.extract(member, extract_to_path)

# Example usage
# extract_directory_from_zip('path/to/zipfile.zip', 'directory_to_extract/', 'path/to/extract/to')

==========================================

import requests
import zipfile
import io
import os

def extract_directory_from_zip_from_url(zip_url, directory_to_extract, extract_to_path):
    # Fetch the content from the URL
    response = requests.get(zip_url)
    response.raise_for_status()  # Ensure the request was successful

    # Treat the fetched content as a file-like object
    zip_file_like = io.BytesIO(response.content)

    with zipfile.ZipFile(zip_file_like) as zip_ref:
        for member in zip_ref.namelist():
            # Check if the file is within the directory to extract
            if member.startswith(directory_to_extract):
                # Extract the file
                zip_ref.extract(member, extract_to_path)

# Example usage
# extract_directory_from_zip_from_url('https://example.com/path/to/zipfile.zip', 'directory_to_extract/', 'path/to/extract/to')

===========================

import requests
import zipfile
import io
import os

def extract_files_flat_from_zip_url(zip_url, directory_to_extract, extract_to_path):
    # Fetch the content from the URL
    response = requests.get(zip_url)
    response.raise_for_status()  # Ensure the request was successful

    # Treat the fetched content as a file-like object
    zip_file_like = io.BytesIO(response.content)

    with zipfile.ZipFile(zip_file_like) as zip_ref:
        for member in zip_ref.namelist():
            # Check if the file is within the directory to extract
            if member.startswith(directory_to_extract):
                # Define the target file path (without original directory structure)
                target_file_path = os.path.join(extract_to_path, os.path.basename(member))

                # Ensure the file is not a directory
                if not member.endswith('/'):
                    with zip_ref.open(member) as source, open(target_file_path, 'wb') as target:
                        # Copy the file content to the target path
                        target.write(source.read())

# Example usage
# extract_files_flat_from_zip_url('https://example.com/path/to/zipfile.zip', 'directory_to_extract/', 'path/to/extract/to')

https://open.spotify.com/track/0k2A09ws6qg8HrMQ07uoMi


https://open.spotify.com/track/6GItErJAg9nBPmiGK0shIj?si=c306195485b74b90


import avro.schema
from avro.datafile import DataFileWriter
from avro.io import DatumWriter
import io

# Define your Avro schema
schema = avro.schema.parse(open("your_schema.avsc", "rb").read())

# Your data to be serialized; it should conform to the Avro schema
data = [
    {"field1": "value1", "field2": 123},  # Example record
    # Add more records as needed
]

# Create an in-memory buffer
buffer = io.BytesIO()

# Use DataFileWriter to write the data and schema to the buffer
with DataFileWriter(buffer, DatumWriter(), schema) as writer:
    for record in data:
        writer.append(record)

# To read from the buffer later, you need to seek back to the start
buffer.seek(0)

# Now `buffer` contains your data in the standard Avro format, including the schema


============================================

from pyspark.sql.types import *
import json

def avro_type_to_spark_type(avro_type):
    """Map Avro types to PySpark types."""
    type_mappings = {
        "string": StringType(),
        "int": IntegerType(),
        "long": LongType(),
        "float": FloatType(),
        "double": DoubleType(),
        "boolean": BooleanType(),
        # Add more mappings as needed
    }
    return type_mappings.get(avro_type, StringType())  # Default to StringType for unknown types

def convert_avro_schema_to_spark_schema(avro_schema):
    """Convert an Avro schema to a PySpark StructType schema."""
    fields = []
    for field in avro_schema['fields']:
        # Handle complex types, arrays, and records recursively if necessary
        spark_type = avro_type_to_spark_type(field['type'])
        fields.append(StructField(field['name'], spark_type, True))
    return StructType(fields)

# Load your Avro schema (assuming JSON format)
avro_schema_path = 'path/to/your/avro_schema.json'
with open(avro_schema_path, 'r') as f:
    avro_schema = json.load(f)

# Convert to PySpark schema
spark_schema = convert_avro_schema_to_spark_schema(avro_schema)

https://open.spotify.com/track/7tEKdpbpU9UehECvFzqeW8?si=d6bca671151044ae

https://open.spotify.com/track/0Gpp7RFAcV7DletYJ6KLIU?si=31e5b699105a4ac4

https://open.spotify.com/track/3zBIvHUKXYWhgJNm7J4hNr?si=9a974ab1528d41e5

https://open.spotify.com/track/1hT3eaGzrcFriQtgGdvsZv?si=ebe79f132b6b4906


import os
from datetime import datetime

def create_version_suffix():
    branch_name = os.environ.get('BRANCH_NAME', '')
    if not branch_name.startswith('release'):
        return '.dev' + datetime.utcnow().strftime('%Y%m%d%H%M%S')
    return ''


====================================

nlu:
- intent: ask_library_hours
  examples: |
    - What are your opening hours?
    - When is the library open?
    - Library timings?

- intent: search_book
  examples: |
    - Do you have [The Great Gatsby](book_name)?
    - I'm looking for a book named [1984](book_name).
    - Is [Harry Potter](book_name) available?

- intent: inquire_membership
  examples: |
    - How do I become a member?
    - Membership details
    - What are the benefits of membership?

===============================================

stories:
- story: library hours inquiry
  steps:
  - intent: ask_library_hours
  - action: utter_library_hours

- story: book search
  steps:
  - intent: search_book
  - action: action_search_book

- story: membership inquiry
  steps:
  - intent: inquire_membership
  - action: utter_membership_details

====================================================

version: "2.0"
intents:
- ask_library_hours
- search_book
- inquire_membership

entities:
- book_name

slots:
  book_name:
    type: text
    influence_conversation: false

responses:
  utter_library_hours:
  - text: "The library is open from 9 AM to 8 PM on weekdays and from 10 AM to 6 PM on weekends."
  
  utter_membership_details:
  - text: "Membership allows you to borrow books, access exclusive online resources, and more. Visit our website or contact us for more information."

actions:
- utter_library_hours
- utter_membership_details
- action_search_book

=================================================================

from typing import Any, Text, Dict, List
from rasa_sdk import Action, Tracker
from rasa_sdk.executor import CollectingDispatcher

class ActionSearchBook(Action):

    def name(self) -> Text:
        return "action_search_book"

    def run(self, dispatcher: CollectingDispatcher,
            tracker: Tracker,
            domain: Dict[Text, Any]) -> List[Dict[Text, Any]]:

        book_name = tracker.get_slot('book_name')  # Retrieve the book name from the slot

        # Here you would add your logic to search for the book. For now, we'll just respond with a placeholder message.
        message = f"Searching for '{book_name}'... (This is a placeholder response.)"

        dispatcher.utter_message(text=message)

        return []

===============================================

version: "3.1"

rules:

# Rule for responding to library hours inquiry
- rule: Respond to library hours inquiry
  steps:
  - intent: ask_library_hours
  - action: utter_library_hours

# Rule for initiating a book search
- rule: Initiate book search
  steps:
  - intent: search_book
  - action: action_search_book

# Rule for providing membership details
- rule: Provide membership details
  steps:
  - intent: inquire_membership
  - action: utter_membership_details

# Fallback rule for unrecognized input
- rule: Fallback
  steps:
  - intent: nlu_fallback
  - action: utter_default


==========================================

curl -XPOST http://localhost:5055/webhook -H "Content-Type: application/json" -d '{
    "next_action": "action_search_book",
    "sender_id": "test_user",
    "tracker": {
        "slots": {
            "book_name": "The Great Gatsby"
        }
    },
    "domain": {
        "entities": [],
        "slots": {},
        "responses": {}
    }
}'

========================================

language: en

pipeline:
  - name: WhitespaceTokenizer
  - name: RegexFeaturizer
  - name: LexicalSyntacticFeaturizer
  - name: CountVectorsFeaturizer
  - name: CountVectorsFeaturizer
    analyzer: char_wb
    min_ngram: 1
    max_ngram: 4
  - name: DIETClassifier
    epochs: 100
    constrain_similarities: true
  - name: EntitySynonymMapper
  - name: ResponseSelector
    epochs: 100
    retrieval_intent: faq
  - name: FallbackClassifier
    threshold: 0.3
    ambiguity_threshold: 0.1

policies:
  - name: MemoizationPolicy
  - name: TEDPolicy
    max_history: 5
    epochs: 100
  - name: RulePolicy

========================================

def test_cassandra_connection():
    from cassandra.cluster import Cluster
    # Connect to the embedded Cassandra instance
    cluster = Cluster(['127.0.0.1'], port=9042)  # Use the correct port
    session = cluster.connect()
    
    # Your test code that interacts with Cassandra goes here
    
    # Clean up
    session.shutdown()
    cluster.shutdown()


import socket
import pytest
import subprocess
import time

def is_port_open(host, port, timeout=2):
    """Check if a port is open on a given host."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.settimeout(timeout)
        if sock.connect_ex((host, port)) == 0:
            return True  # The port is open
    return False  # The port is not open

@pytest.fixture(scope="session", autouse=True)
def start_embedded_cassandra():
    jar_path = "path/to/your/cassandra-unit-jar.jar"
    
    # Start the embedded Cassandra as a subprocess
    cassandra_process = subprocess.Popen(["java", "-jar", jar_path])
    
    # Wait for Cassandra's port to be open (polling)
    host, port = "127.0.0.1", 9042  # Adjust the port if necessary
    timeout = 120  # Total timeout in seconds to wait for Cassandra to start
    start_time = time.time()
    
    while True:
        if is_port_open(host, port):
            break  # The port is open, Cassandra is ready
        elif time.time() - start_time > timeout:
            raise TimeoutError(f"Cassandra did not start within {timeout} seconds")
        time.sleep(1)  # Wait a bit before trying again

    yield  # Yield control back to the test function
    
    # Terminate the Cassandra process after the tests
    cassandra_process.terminate()
    cassandra_process.wait()

    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <configuration>
          <shadedArtifactAttached>true</shadedArtifactAttached>
          <shadedClassifierName>bin</shadedClassifierName>
          <createDependencyReducedPom>false</createDependencyReducedPom>
          <filters>
            <filter>
              <artifact>*:*</artifact>
              <excludes>
                <exclude>META-INF/*.SF</exclude>
                <exclude>META-INF/*.DSA</exclude>
                <exclude>META-INF/*.RSA</exclude>
              </excludes>
            </filter>
          </filters>
        </configuration>
        <executions>
          <execution>
            <configuration>
              <transformers>
                <transformer implementation="org.apache.maven.plugins.shade.resource.AppendingTransformer">
                  <resource>reference.conf</resource>
                </transformer>
                <transformer implementation="org.apache.maven.plugins.shade.resource.AppendingTransformer">
                  <resource>META-INF/spring.handlers</resource>
                </transformer>
                <transformer implementation="org.apache.maven.plugins.shade.resource.AppendingTransformer">
                  <resource>META-INF/spring.schemas</resource>
                </transformer>
                <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer" />
                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                  <mainClass>${mainclass}</mainClass>
                </transformer>
              </transformers>
            </configuration>
          </execution>
        </executions>
      </plugin>

import pytest
import subprocess
import time
import socket
from cassandra.cluster import Cluster

def wait_for_port(port, host='localhost', timeout=60.0):
    """Wait until a port starts accepting TCP connections."""
    start_time = time.time()
    while True:
        try:
            with socket.create_connection((host, port), timeout=timeout):
                break
        except OSError as ex:
            time.sleep(0.01)
            if time.time() - start_time >= timeout:
                raise TimeoutError(f'Waited too long for the port {port} on {host} to start accepting connections.')

@pytest.fixture(scope="session")
def cassandra_unit():
    # Define the startup command with all necessary Java VM arguments
    jar_path = "path/to/your/cassandra-unit-jar.jar"  # Change this to the actual path of your JAR file
    command = ["java", "-jar", jar_path, "additional", "arguments"]

    # Start Cassandra unit
    process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    try:
        # Wait for Cassandra to be ready (port 9142 by default)
        wait_for_port(9142)

        # Connect to Cassandra and create a keyspace
        cluster = Cluster(['127.0.0.1'], port=9142)
        session = cluster.connect()
        session.execute("CREATE KEYSPACE IF NOT EXISTS test_keyspace WITH replication = {'class':'SimpleStrategy', 'replication_factor' : 3};")

        yield  # Provide the fixture value

    finally:
        # Shutdown Cassandra unit after tests
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()

# Example usage in a test
def test_cassandra_operation(cassandra_unit):
    # Your test code here, using the Cassandra keyspace
    pass


==========================================================================

import subprocess
import threading

# Function to continuously read from a stream
def read_stream(stream, display=True):
    while True:
        line = stream.readline()
        if not line:
            break
        if display:
            print(line.decode().strip())

# Start the subprocess with stdout and stderr piped
process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

# Create and start threads to read stdout and stderr
stdout_thread = threading.Thread(target=read_stream, args=(process.stdout,))
stderr_thread = threading.Thread(target=read_stream, args=(process.stderr, False))  # Set display=False if you don't want to print stderr

stdout_thread.start()
stderr_thread.start()

# Wait for the threads to finish
stdout_thread.join()
stderr_thread.join()

# Wait for the subprocess to finish if needed
process.wait()

=============

import asyncio
from threading import Thread
import pytest
from rasa.core.run import serve_application

def run_rasa_server():
    # Define the coroutine that runs the Rasa server
    async def server_coroutine():
        # The serve_application function starts the Rasa server. Adjust the parameters as needed.
        # Note: `model` should point to the directory containing your trained model.
        await serve_application(model="path/to/your/model")

    # Run the server coroutine in the current event loop
    asyncio.run(server_coroutine())

@pytest.fixture(scope="session")
def rasa_server():
    # Start the Rasa server in a background thread
    thread = Thread(target=run_rasa_server)
    thread.daemon = True  # Daemon threads are terminated when the main program exits
    thread.start()

    # Wait a bit for the server to start up (consider a more reliable check here)
    time.sleep(10)  # Adjust the sleep time as needed

    yield  # This allows the tests to run while the server is up

    # The server will automatically stop when the pytest session ends, as the thread is a daemon

============

import threading
import functools
import pytest

def other_thread(test_func):
    @functools.wraps(test_func)
    def wrapper(*args, **kwargs):
        # Container for exceptions
        exceptions = []

        def run():
            try:
                test_func(*args, **kwargs)
            except Exception as e:
                exceptions.append(e)

        t = threading.Thread(target=run)
        t.start()
        t.join()

        if exceptions:
            raise exceptions[0]  # Re-raise the first exception in the main thread

    return wrapper


========================

def wrapper(*args, **kwargs):
    # Iterate through positional arguments in args
    for arg in args:
        if isinstance(arg, DesiredType):  # Check if arg is of the type you're looking for
            # Do something with arg
            print("Found in args:", arg)
    
    # Iterate through keyword arguments in kwargs
    for key, value in kwargs.items():
        if isinstance(value, DesiredType):  # Check if value is of the type you're looking for
            # Do something with value
            print(f"Found in kwargs under '{key}':", value)

# Example usage
class DesiredType:
    def __init__(self, name):
        self.name = name

    def __repr__(self):
        return f"DesiredType({self.name})"

def my_function(*args, **kwargs):
    wrapper(*args, **kwargs)

obj1 = DesiredType("obj1")
obj2 = DesiredType("obj2")

my_function(obj1, not_target="hello", target=obj2)

======================

import requests
import xml.etree.ElementTree as ET

def getSnapshotUrl(maven_snapshot_repo_url, groupId, artifactId, version, classifier=None):
    # Replace dots and colons in groupId and version to construct the metadata URL
    metadata_url = f"{maven_snapshot_repo_url}/{groupId.replace('.', '/')}/{artifactId}/{version}/maven-metadata.xml"

    # Fetch the metadata XML
    response = requests.get(metadata_url)
    if response.status_code != 200:
        raise Exception(f"Failed to fetch maven metadata from {metadata_url}")

    # Parse the XML
    metadata_xml = ET.fromstring(response.content)

    # Find the latest snapshot version
    versioning = metadata_xml.find('versioning')
    snapshot = versioning.find('snapshot')
    timestamp = snapshot.find('timestamp').text
    buildNumber = snapshot.find('buildNumber').text

    # Construct the artifact filename
    snapshotVersion = version.replace("-SNAPSHOT", f"-{timestamp}-{buildNumber}")
    if classifier:
        artifact_filename = f"{artifactId}-{snapshotVersion}-{classifier}.jar"
    else:
        artifact_filename = f"{artifactId}-{snapshotVersion}.jar"

    # Construct the snapshot URL
    snapshot_url = f"{maven_snapshot_repo_url}/{groupId.replace('.', '/')}/{artifactId}/{version}/{artifact_filename}"

    return snapshot_url

# Example usage
maven_snapshot_repo_url = "https://your.maven.repo.url/repository/snapshots"
groupId = "com.example"
artifactId = "your-artifact"
version = "1.0-SNAPSHOT"
classifier = "jar-with-dependencies"  # Optional, can be None

try:
    snapshot_url = getSnapshotUrl(maven_snapshot_repo_url, groupId, artifactId, version, classifier)
    print(snapshot_url)
except Exception as e:
    print(e)

=========================

import os
import requests

def fetchJarFromMaven(location_to_download_to, maven_repo_url, groupId, artifactId, version, classifier=None):
    # Construct the URL for the JAR file
    group_path = groupId.replace('.', '/')
    classifier_part = f"-{classifier}" if classifier else ""
    jar_url = f"{maven_repo_url}/{group_path}/{artifactId}/{version}/{artifactId}-{version}{classifier_part}.jar"

    # Make the request to download the JAR
    response = requests.get(jar_url, stream=True)
    if response.status_code == 200:
        # Ensure the download directory exists
        os.makedirs(location_to_download_to, exist_ok=True)

        # Construct the full path for the downloaded file
        filename = f"{artifactId}-{version}{classifier_part}.jar"
        file_path = os.path.join(location_to_download_to, filename)

        # Write the JAR file to the specified location
        with open(file_path, 'wb') as f:
            for chunk in response.iter_content(chunk_size=128):
                f.write(chunk)

        print(f"Downloaded: {file_path}")
    else:
        print(f"Failed to download JAR from {jar_url}. HTTP status code: {response.status_code}")

# Example usage
fetchJarFromMaven(
    location_to_download_to='/path/to/download',
    maven_repo_url='https://repo1.maven.org/maven2',
    groupId='org.apache.commons',
    artifactId='commons-lang3',
    version='3.12.0'
)

===========================================

import threading
from rasa_sdk.executor import ActionExecutor
from rasa_sdk.endpoint import endpoint_app
from rasa_sdk.interfaces import Action
from sanic import Sanic

# Assuming ActionSearchBook is defined elsewhere
from your_action_file import ActionSearchBook

def start_action_server():
    # Load your custom actions
    executor = ActionExecutor()
    executor.register_package(ActionSearchBook)
    
    # Create a Sanic app with the registered actions
    app = endpoint_app(cors_origins="*", action_executor=executor)
    
    # Start the Sanic server on a separate thread
    app.run(host="0.0.0.0", port=5055, access_log=False)

# Function to run the action server in a separate thread
def run_server_in_thread():
    thread = threading.Thread(target=start_action_server)
    thread.daemon = True  # Allows the server to shut down when the main thread exits
    thread.start()
    
    # Wait a bit for the server to start up (optional, depending on your setup)
    time.sleep(2)

# Your test function
def test_action_search_book():
    # Start the action server
    run_server_in_thread()
    
    # Here you would add your code to interact with the action server
    # For example, you might send a POST request to 'http://localhost:5055/webhook'
    # with the appropriate payload and then check the response

    # Don't forget to include logic to stop the server or ensure it stops when the test completes

===============

#!/bin/bash

# Find all test files
test_files=$(find . -name 'test_*.py')

# Initialize a flag to track the first iteration
first_run=true

# Loop through all test files
for file in $test_files; do
    if [ "$first_run" = true ]; then
        # Run pytest without --cov-append for the first test file
        pytest --cov=your_package_name --cov-report= $file
        first_run=false
    else
        # Run pytest with --cov-append for subsequent test files
        pytest --cov=your_package_name --cov-report= --cov-append $file
    fi
done

# After running all tests, generate the final aggregated coverage report in XML format
coverage xml -o coverage_report.xml

================================

import requests

def download_file(url, local_filename):
    # Send a GET request to the URL
    with requests.get(url, stream=True) as r:
        r.raise_for_status()  # Check for errors
        
        # Open a local file with write-binary mode
        with open(local_filename, 'wb') as f:
            for chunk in r.iter_content(chunk_size=8192): 
                # If you have chunk encoded response uncomment if
                # and set chunk_size parameter to None.
                #if chunk: 
                f.write(chunk)

# Example usage
url = "YOUR_FILE_URL_HERE"
local_filename = url.split('/')[-1]  # Use the last part of the URL as the file name
download_file(url, local_filename)

import yaml

def read_yaml_to_dict(filename):
    with open(filename, 'r') as stream:
        try:
            return yaml.safe_load(stream)
        except yaml.YAMLError as exc:
            print(exc)
            return None

# Example usage
yaml_file = 'your_yaml_file.yaml'  # Replace with your YAML file path
yaml_dict = read_yaml_to_dict(yaml_file)
print(yaml_dict)

==========

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class FileDownloader {

    /**
     * Downloads a file from a given URL to a specified destination directory.
     *
     * @param fileURL         The URL of the file to download.
     * @param destinationDir  The directory where the file will be saved.
     * @throws IOException if an I/O error occurs.
     */
    public static void downloadFile(String fileURL, String destinationDir) throws IOException {
        // Extract the file name from the URL
        String fileName = fileURL.substring(fileURL.lastIndexOf('/') + 1);

        // Create a Path object for the destination directory and resolve the filename within it
        Path destinationPath = Paths.get(destinationDir).resolve(fileName);

        // Open a stream from the URL
        try (InputStream in = new URL(fileURL).openStream()) {
            // Copy the file from the stream to the destination path
            Files.copy(in, destinationPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("File downloaded to: " + destinationPath);
        }
    }

    public static void main(String[] args) {
        String fileURL = "https://example.com/path/to/your/file.txt"; // Replace with the actual file URL
        String destinationDir = "path/to/destination/directory"; // Replace with the actual destination directory

        try {
            downloadFile(fileURL, destinationDir);
        } catch (IOException e) {
            System.err.println("An error occurred during file download: " + e.getMessage());
        }
    }
}

=========================

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TarGzExtractor {

    public static void extractTarGz(File tarGzFile, Path destination) throws IOException {
        try (FileInputStream fis = new FileInputStream(tarGzFile);
             BufferedInputStream bis = new BufferedInputStream(fis);
             GzipCompressorInputStream gis = new GzipCompressorInputStream(bis);
             TarArchiveInputStream tarInput = new TarArchiveInputStream(gis)) {

            TarArchiveEntry entry;
            while ((entry = (TarArchiveEntry) tarInput.getNextEntry()) != null) {
                Path outputPath = destination.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                        tarInput.transferTo(fos);
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        File tarGzFile = new File("path/to/your/file.tar.gz"); // Replace with your tar.gz file path
        Path destination = Path.of("path/to/extract/contents"); // Replace with your destination directory path

        try {
            extractTarGz(tarGzFile, destination);
            System.out.println("Extraction completed.");
        } catch (IOException e) {
            System.err.println("Error occurred during extraction: " + e.getMessage());
        }
    }
}

=============

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class PortListenerWaiter {

    public static boolean waitForPort(String host, int port, int timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        IOException lastException = null;

        while (System.currentTimeMillis() < deadline) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 1000); // Attempt to connect with a 1-second timeout
                return true; // Connection successful, the process is listening on the port
            } catch (IOException e) {
                lastException = e; // Remember the last exception for potential debugging
                try {
                    Thread.sleep(1000); // Wait for a second before retrying
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); // Preserve interrupt status
                    return false; // Interrupted, exit the loop
                }
            }
        }

        // Timeout period elapsed, log or handle the last exception if needed
        if (lastException != null) {
            System.err.println("Failed to connect to " + host + ":" + port + " within " + timeoutMillis + " ms. Last exception: " + lastException.getMessage());
        }
        return false; // Timeout reached without successful connection
    }

    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 8080;
        int timeoutMillis = 30000; // 30 seconds

        if (waitForPort(host, port, timeoutMillis)) {
            System.out.println("Process is now listening on port " + port);
        } else {
            System.out.println("Timed out waiting for a process to listen on port " + port);
        }
    }
}

============

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

public class FileModeSetter {

    public static void setFilePermissions(File file, int mode) throws IOException {
        Set<PosixFilePermission> perms = new HashSet<>();

        // Owner permissions
        if ((mode & 0400) > 0) perms.add(PosixFilePermission.OWNER_READ);
        if ((mode & 0200) > 0) perms.add(PosixFilePermission.OWNER_WRITE);
        if ((mode & 0100) > 0) perms.add(PosixFilePermission.OWNER_EXECUTE);

        // Group permissions
        if ((mode & 0040) > 0) perms.add(PosixFilePermission.GROUP_READ);
        if ((mode & 0020) > 0) perms.add(PosixFilePermission.GROUP_WRITE);
        if ((mode & 0010) > 0) perms.add(PosixFilePermission.GROUP_EXECUTE);

        // Others permissions
        if ((mode & 0004) > 0) perms.add(PosixFilePermission.OTHERS_READ);
        if ((mode & 0002) > 0) perms.add(PosixFilePermission.OTHERS_WRITE);
        if ((mode & 0001) > 0) perms.add(PosixFilePermission.OTHERS_EXECUTE);

        // Apply the permissions
        Files.setPosixFilePermissions(file.toPath(), perms);
    }
}

============


import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.gaul.s3proxy.S3Proxy;
import org.jclouds.blobstore.BlobStoreContext;

public class S3ProxyTestSetup {

    private S3Proxy s3Proxy;
    private BlobStoreContext context;
    private String endpoint;

    @Before
    public void setUp() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("s3proxy.authorization", "none");
        properties.setProperty("s3proxy.endpoint", "http://127.0.0.1:0"); // Use 0 for random port
        properties.setProperty("jclouds.provider", "filesystem");
        properties.setProperty("jclouds.filesystem.basedir", "/path/to/your/target/subdirectory");

        context = ContextBuilder.newBuilder("filesystem")
                .overrides(properties)
                .buildView(BlobStoreContext.class);
        
        s3Proxy = S3Proxy.builder()
                .blobStore(context.getBlobStore())
                .endpoint(URI.create(properties.getProperty("s3proxy.endpoint")))
                .build();
        s3Proxy.start();
        while (!s3Proxy.getState().equals(AbstractLifeCycle.STARTED)) {
            Thread.sleep(10); // wait for the server to start
        }

        endpoint = s3Proxy.getUri().toString();
        // Configure your application to use `endpoint` as the S3 endpoint
    }

    @After
    public void tearDown() throws Exception {
        if (s3Proxy != null) {
            s3Proxy.stop();
        }
        if (context != null) {
            context.close();
        }
    }
}

==================

PropertyFilter ignoreFieldsFilter = new SimpleBeanPropertyFilter() {
    @Override
    public void serializeAsField(Object pojo, JsonGenerator jgen, SerializerProvider provider, PropertyWriter writer) throws Exception {
        if (fieldsToIgnore.contains(writer.getName())) {
            // Skip this field
        } else {
            // Serialize as normal
            writer.serializeAsField(pojo, jgen, provider);
        }
    }
};

ObjectMapper mapper = new ObjectMapper();
FilterProvider filters = new SimpleFilterProvider().addFilter("ignoreFieldsFilter", ignoreFieldsFilter);
mapper.setFilterProvider(filters);

// Important: Enable the filter for all classes
mapper.setAnnotationIntrospector(new JacksonAnnotationIntrospector() {
    @Override
    public Object findFilterId(Annotated a) {
        return "ignoreFieldsFilter"; // Return the ID of the filter to apply
    }
});

======================

import boto3

# Initialize a boto3 client
s3_client = boto3.client('s3')

# Source S3 object details
source_bucket = 'your-source-bucket'
source_key = 'your-source-object-key'

# Destination S3 object details
destination_bucket = 'your-destination-bucket'
destination_key = 'your-destination-object-key'

# Start a multipart upload session
multipart_upload = s3_client.create_multipart_upload(Bucket=destination_bucket, Key=destination_key)
parts = []

# Get the source object
source_object = s3_client.get_object(Bucket=source_bucket, Key=source_key)

part_number = 1
# The buffer to hold data for each part
buffer = io.BytesIO()

# Process the source object line by line
for line in source_object['Body'].iter_lines():
    # Process the line (example: converting to uppercase)
    processed_line = line.decode('utf-8').upper() + '\n'
    # Write the processed line to the buffer
    buffer.write(processed_line.encode('utf-8'))

    # If buffer size exceeds a certain threshold, upload the part
    if buffer.tell() > 5 * 1024 * 1024:  # For example, 5MB
        buffer.seek(0)
        # Upload part
        part = s3_client.upload_part(Bucket=destination_bucket, Key=destination_key, 
                                     PartNumber=part_number, UploadId=multipart_upload['UploadId'], 
                                     Body=buffer)
        parts.append({"PartNumber": part_number, "ETag": part['ETag']})
        part_number += 1
        buffer = io.BytesIO()  # Reset buffer

# Upload any remaining part in buffer
if buffer.tell() > 0:
    buffer.seek(0)
    part = s3_client.upload_part(Bucket=destination_bucket, Key=destination_key,
                                 PartNumber=part_number, UploadId=multipart_upload['UploadId'],
                                 Body=buffer)
    parts.append({"PartNumber": part_number, "ETag": part['ETag']})

# Complete multipart upload
s3_client.complete_multipart_upload(Bucket=destination_bucket, Key=destination_key, 
                                    UploadId=multipart_upload['UploadId'], MultipartUpload={"Parts": parts})

==================

import boto3
import io

# Initialize a boto3 client
s3_client = boto3.client('s3')

# Specify your source and destination details
source_bucket = 'your-source-bucket'
source_key = 'your-source-object-key'
destination_bucket = 'your-destination-bucket'
destination_key = 'your-destination-object-key'

# Get the source object
source_object = s3_client.get_object(Bucket=source_bucket, Key=source_key)

# Use a BytesIO stream as an intermediary to write processed lines
with io.BytesIO() as file_stream:
    # Process the source object line by line
    for line in source_object['Body'].iter_lines():
        # Convert bytes to string and process the line
        processed_line = line.decode('utf-8').upper() + '\n'  # Example processing
        # Write the processed line to the BytesIO stream
        file_stream.write(processed_line.encode('utf-8'))
    
    # After processing all lines, seek back to the beginning of the BytesIO stream
    file_stream.seek(0)
    
    # Upload the stream directly to the new S3 object
    s3_client.upload_fileobj(file_stream, Bucket=destination_bucket, Key=destination_key)

==========================

def stream_s3_object_to_file(bucket_name, object_key, local_file_path):
    """Stream an S3 object line by line to a local file."""
    with open(local_file_path, 'w') as out_file:
        # Get the S3 object
        s3_object = s3.get_object(Bucket=bucket_name, Key=object_key)
        # Ensure streaming by accessing the Body
        for line in s3_object['Body'].iter_lines():
            # Decode binary to string and process the line
            processed_line = process_line(line.decode('utf-8'))
            # Write the processed line to the output file
            out_file.write(processed_line + '\n')


=====================

import requests
from xml.etree import ElementTree

def download_jar_from_maven(repo_url, group_id, artifact_id, version, classifier=None):
    # Convert group_id to URL path format
    group_url = group_id.replace('.', '/')
    base_url = f"{repo_url}/{group_url}/{artifact_id}/{version}"
    
    if "SNAPSHOT" in version:
        # Fetch maven-metadata.xml to get the specific snapshot version
        metadata_url = f"{base_url}/maven-metadata.xml"
        response = requests.get(metadata_url)
        if response.status_code == 200:
            xml_root = ElementTree.fromstring(response.content)
            timestamp = xml_root.find(".//timestamp").text
            buildNumber = xml_root.find(".//buildNumber").text
            # Replace SNAPSHOT in version with actual snapshot version
            specific_version = version.replace("SNAPSHOT", f"{timestamp}-{buildNumber}")
        else:
            print("Failed to fetch maven-metadata.xml")
            return
    else:
        specific_version = version
    
    jar_filename = f"{artifact_id}-{specific_version}"
    if classifier:
        jar_filename += f"-{classifier}"
    jar_filename += ".jar"
    
    jar_url = f"{base_url}/{jar_filename}"
    
    # Download the JAR file
    print(f"Downloading {jar_url}")
    response = requests.get(jar_url)
    if response.status_code == 200:
        with open(jar_filename, 'wb') as f:
            f.write(response.content)
        print(f"Downloaded {jar_filename}")
    else:
        print(f"Failed to download {jar_url}")

# Example usage
# download_jar_from_maven('https://repo1.maven.org/maven2', 'com.example', 'my-artifact', '1.0-SNAPSHOT')

https://www.youtube.com/watch?v=7QwUCR1NHpg

f#hPB1yZxooOG9hmW!1XeacUmAuKA7

=================================================

public class CustomValidationException extends RuntimeException {
    private final String fieldName;
    private final Object rejectedValue;
    private final String message;

    public CustomValidationException(String fieldName, Object rejectedValue, String message) {
        super(message);
        this.fieldName = fieldName;
        this.rejectedValue = rejectedValue;
        this.message = message;
    }

    // Getters
    public String getFieldName() {
        return fieldName;
    }

    public Object getRejectedValue() {
        return rejectedValue;
    }

    @Override
    public String getMessage() {
        return message;
    }
}


@ControllerAdvice
public class CustomExceptionHandler {

    @ExceptionHandler(CustomValidationException.class)
    public ResponseEntity<Object> handleCustomValidationException(CustomValidationException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("message", "Validation failed for object='yourObject'. Error count: 1");
        body.put("errors", List.of(Map.of(
                "field", ex.getFieldName(),
                "rejectedValue", ex.getRejectedValue(),
                "message", ex.getMessage()
        )));

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }
}

