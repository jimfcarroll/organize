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
