FROM node:18-alpine AS build
# RUN mkdir -p /app
WORKDIR /app
# COPY package*.json ./
COPY . .
RUN npm install
RUN npm install -g @angular/cli
# RUN npm start --configuration=production
CMD ng serve --configuration=production --host 0.0.0.0 --port 4200

# running with below commands does not work.
# CMD ["npm", "start"]
# CMD ["npm", "start", "--configuration=production"]


# this steps work
#stage 2
# FROM nginx:alpine
# COPY --from=build /app/dist/web-app /usr/share/nginx/html









