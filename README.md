# Fare-site

Apps web de Faresite publicadas con GitHub Pages.

- `audio-fare/`: Audio Fare, reproductor de música personal. Las canciones se guardan solo en el teléfono de quien la usa; nada se sube a este repositorio ni a ningún servidor.

## Armario Fare

`armario-fare/`: app para administrar la ropa (prendas, conjuntos posibles, cuántas veces se ha puesto cada prenda y qué toca lavar). Los datos se guardan en el teléfono. Para publicar una versión nueva: subir a `main` y cambiar la versión de caché en `armario-fare/sw.js`.

## Armario Fare Beta

`armario-fare-beta/`: versión de prueba de Armario Fare (https://q12-maker.github.io/Fare-site/armario-fare-beta/), enfocada en ver la ropa y no repetir prendas por error: avisa qué te pusiste en los últimos días (3 por defecto, se cambia en Ajustes) y pide confirmar si vas a repetir. Logo discreto (Logo sombra de fondo, sin logo en la barra). Comparte los datos con la oficial porque viven en el mismo sitio. Pasa a `armario-fare/` solo cuando Juan lo revise. Para publicar: subir a `main` y cambiar la versión de caché en `armario-fare-beta/sw.js`.

## Audio Fare Beta

`audio-fare-beta/`: versión de prueba de Audio Fare (https://q12-maker.github.io/Fare-site/audio-fare-beta/). Todo cambio va primero aquí; pasa a `audio-fare/` solo cuando Juan lo revise. Comparte la música y los ajustes con la oficial porque viven en el mismo sitio. Para publicar: subir a `main` y cambiar la versión de caché en `audio-fare-beta/sw.js`.

## Faresite Beta

`faresite-beta/`: página web de la agencia Faresite (https://q12-maker.github.io/Fare-site/faresite-beta/). Servicios: página web, app de administración, WhatsApp que trabaja solo y asistente con IA (adicional). Todo cambio va primero aquí; la versión oficial se crea solo cuando Juan la revise. Para publicar una versión nueva: subir a `main` y cambiar la versión de caché en `faresite-beta/sw.js`. El número de WhatsApp de Faresite va en la variable `WHATSAPP` de `index.html` (hoy: el WhatsApp personal de Juan).

## Faresite (oficial)

`faresite/`: versión oficial de la web de Faresite (https://q12-maker.github.io/Fare-site/faresite/). Solo recibe cambios ya revisados por Juan en `faresite-beta/`. Al publicar: copiar desde la Beta (sin la etiqueta BETA) y subir la versión de caché en `faresite/sw.js`.

## Faresite en Cloudflare (sin "q12-maker" en la dirección)

`.github/workflows/faresite-cloudflare.yml` publica `faresite/` en https://faresite.pages.dev y `faresite-beta/` en https://faresite-beta.pages.dev cada vez que cambian en `main`. Necesita los secretos `CF_TOKEN` (token de Cloudflare solo con permiso Cloudflare Pages: Edit) y `CF_CUENTA` (Account ID) en este repositorio.
