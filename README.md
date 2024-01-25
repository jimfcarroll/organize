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
