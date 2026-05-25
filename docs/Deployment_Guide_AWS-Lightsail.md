cat > ~/deploy-plp-frontend.sh << 'SCRIPT'
#!/bin/bash
# PLP Frontend Deploy Script
# Usage: ./deploy-plp-frontend.sh <zip-file-path>
# Example: ./deploy-plp-frontend.sh ~/program_lending_frontend.zip

ZIP=$1

if [ -z "$ZIP" ]; then
  echo "Usage: ./deploy-plp-frontend.sh <zip-file-path>"
  exit 1
fi

echo "===================================="
echo " PLP Frontend Deploy"
echo "===================================="

# Extract
echo "[1/6] Extracting zip..."
rm -rf ~/plp_frontend_deploy
unzip -q "$ZIP" -d ~/plp_frontend_deploy

# Find the packages folder
PACKAGES=$(find ~/plp_frontend_deploy -type d -name "packages" | head -1)
if [ -z "$PACKAGES" ]; then
  echo "ERROR: Could not find packages folder in zip"
  exit 1
fi

echo "[2/6] Copying source files..."
cp -r "$PACKAGES/shared" ~/plp/frontend/packages/
cp -r "$PACKAGES/platform-ui" ~/plp/frontend/packages/
cp -r "$PACKAGES/anchor-portal" ~/plp/frontend/packages/
cp -r "$PACKAGES/borrower-portal" ~/plp/frontend/packages/

echo "[3/6] Applying server config patches..."
# Fix logo paths
sed -i "s|'/assets/branding/BillionLoans_Logo_Final_noBG.png'|'./assets/branding/BillionLoans_Logo_Final_noBG.png'|g" ~/plp/frontend/packages/shared/src/components/portalBranding.tsx
sed -i "s|'/assets/branding/BillionTech_Logo_Final.png'|'./assets/branding/BillionTech_Logo_Final.png'|g" ~/plp/frontend/packages/shared/src/components/portalBranding.tsx

# Fix BrowserRouter basenames (only if not already set)
grep -q 'basename="/plp"' ~/plp/frontend/packages/platform-ui/src/main.tsx || sed -i 's/<BrowserRouter>/<BrowserRouter basename="\/plp">/' ~/plp/frontend/packages/platform-ui/src/main.tsx
grep -q 'basename="/plp-anchor"' ~/plp/frontend/packages/anchor-portal/src/main.tsx || sed -i 's/<BrowserRouter>/<BrowserRouter basename="\/plp-anchor">/' ~/plp/frontend/packages/anchor-portal/src/main.tsx
grep -q 'basename="/plp-borrower"' ~/plp/frontend/packages/borrower-portal/src/main.tsx || sed -i 's/<BrowserRouter>/<BrowserRouter basename="\/plp-borrower">/' ~/plp/frontend/packages/borrower-portal/src/main.tsx

# Fix vite base paths (only if not already set)
grep -q 'base:' ~/plp/frontend/packages/platform-ui/vite.config.ts || sed -i 's/defineConfig({/defineConfig({ base: "\/plp\/",/' ~/plp/frontend/packages/platform-ui/vite.config.ts
grep -q 'base:' ~/plp/frontend/packages/anchor-portal/vite.config.ts || sed -i 's/defineConfig({/defineConfig({ base: "\/plp-anchor\/",/' ~/plp/frontend/packages/anchor-portal/vite.config.ts
grep -q 'base:' ~/plp/frontend/packages/borrower-portal/vite.config.ts || sed -i 's/defineConfig({/defineConfig({ base: "\/plp-borrower\/",/' ~/plp/frontend/packages/borrower-portal/vite.config.ts

# Fix API base URL
echo "VITE_API_BASE_URL=/plp-api" > ~/plp/frontend/packages/platform-ui/.env.production
echo "VITE_API_BASE_URL=/plp-api" > ~/plp/frontend/packages/anchor-portal/.env.production
echo "VITE_API_BASE_URL=/plp-api" > ~/plp/frontend/packages/borrower-portal/.env.production

echo "[4/6] Installing dependencies..."
cd ~/plp/frontend && npm install --silent
cd ~/plp/frontend/packages/platform-ui && npm install --silent
cd ~/plp/frontend/packages/anchor-portal && npm install --silent
cd ~/plp/frontend/packages/borrower-portal && npm install --silent

echo "[5/6] Building all portals..."
cd ~/plp/frontend/packages/platform-ui && npm run build 2>&1 | tail -2
cd ~/plp/frontend/packages/anchor-portal && npm run build 2>&1 | tail -2
cd ~/plp/frontend/packages/borrower-portal && npm run build 2>&1 | tail -2

echo "[6/6] Deploying to Nginx..."
sudo cp -r ~/plp/frontend/packages/platform-ui/dist/* /var/www/plp/platform-ui/
sudo cp -r ~/plp/frontend/packages/anchor-portal/dist/* /var/www/plp/anchor-portal/
sudo cp -r ~/plp/frontend/packages/borrower-portal/dist/* /var/www/plp/borrower-portal/

# Cleanup
rm -rf ~/plp_frontend_deploy

echo "===================================="
echo " Deploy Complete!"
echo " Platform Admin : http://13.126.73.184/plp/"
echo " Anchor Portal  : http://13.126.73.184/plp-anchor/"
echo " Borrower Portal: http://13.126.73.184/plp-borrower/"
echo "===================================="
SCRIPT
chmod +x ~/deploy-plp-frontend.sh



Then verify it was created:


ls -la ~/deploy-plp-frontend.sh