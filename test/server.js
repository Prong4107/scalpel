const express = require('express')
const app = express()
const port = 3000

app.all('/', (req, res) => {
    req.
  res.send({url: req.url, headers: req.headers, body: req.body})
})

app.listen(port, () => {
  console.log(`Example app listening on port ${port}`)
})
