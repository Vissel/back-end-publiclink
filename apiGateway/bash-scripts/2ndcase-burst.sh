# Run 50 times without sleep
echo "Running 51 times without sleep..."
for i in $(seq 1 50); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/server-auth/public-key)
  if [ "$i" -le 3 ] || [ "$i" -ge 49 ]; then
    echo "Request #$i: Status code $STATUS"
  elif [ "$i" -eq 4 ]; then
    echo "...running..."
  fi
done

# Run 52 times with 3s sleep
echo "Running no 52 times. Verifying 429 is returned..."
curl -s -D - http://localhost:8080/api/v1/server-auth/public-key 2>&1 | head -20

# Run 52 times with 3s sleep
#echo "Running no 53 times. Verifying refill after sleep 3 s..."
#sleep 3
#curl -s -D - http://localhost:8080/api/v1/server-auth/public-keyy 2>&1 | head -20
