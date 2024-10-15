from datetime import datetime
import pytz

# Original date string and format
original_date_str = "2023-08-26T14:30:45.123Z"
original_format = "%Y-%m-%dT%H:%M:%S.%fZ"

# Desired output format
output_format = "Value as of %I:%M %p %Z %m/%d/%Y"

# Parse the original string to a datetime object in UTC time zone
utc_time = datetime.strptime(original_date_str, original_format).replace(tzinfo=pytz.UTC)

# Convert to Eastern Time (New York)
eastern = pytz.timezone('America/New_York')
eastern_time = utc_time.astimezone(eastern)

# Format the datetime object to the desired format
formatted_date = eastern_time.strftime(output_format)

print(formatted_date)
