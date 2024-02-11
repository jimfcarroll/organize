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
