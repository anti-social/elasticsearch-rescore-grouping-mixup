[![Travis Status](https://travis-ci.org/anti-social/elasticsearch-rescore-grouping-mixup.svg?branch=master)](https://travis-ci.org/anti-social/elasticsearch-rescore-grouping-mixup)

# Grouping mixup rescorer

This is a rescoring plugin for Elasticsearch that can mix up search results. For example you have
products from several companies and you want that one company couldn't displace the others.

You can assemble plugin with gradle or vagga:

```
./gradlew assemble
```

Either inside container:

```
vagga assemble
```

To run tests just execute:

```
./gradlew test integTest
```

Or:

```
vagga test
```

To test the plugin with Elasticsearch run:

```
vagga elastic
```

Create an index and put some documents into it:

```
curl -X PUT -H 'Content-Type: application/yaml' 'localhost:9200/bikeshop' --data-binary '---
mappings:
  _doc:
    _routing:
      required: true
    properties:
      name:
        type: text
      manufacturer:
        type: keyword
'

curl -X PUT -H 'Content-Type: application/yaml' 'localhost:9200/bikeshop/_doc/1?routing=giant' --data-binary '---
name: Giant XTC jr 26+
manufacturer: giant
rank: 4.5
'
curl -X PUT -H 'Content-Type: application/yaml' 'localhost:9200/bikeshop/_doc/2?routing=giant' --data-binary '---
name: Giant XTC jr 24+
manufacturer: giant
rank: 4.4
'
curl -X PUT -H 'Content-Type: application/yaml' 'localhost:9200/bikeshop/_doc/3?routing=norco' --data-binary '---
name: Norco CHARGER 4.1
manufacturer: norco
rank: 4.2
'
curl -X PUT -H 'Content-Type: application/yaml' 'localhost:9200/bikeshop/_doc/4?routing=norco' --data-binary '---
name: Norco STORM 4.1
manufacturer: norco
rank: 4.1
'
curl -X PUT -H 'Content-Type: application/yaml' 'localhost:9200/bikeshop/_doc/5?routing=gt' --data-binary '---
name: GT STOMPER MAX 26
manufacturer: gt
rank: 4.0
'
```

Ok, let's search our bikes:

```
curl -X GET -H 'Content-Type: application/yaml' 'localhost:9200/bikeshop/_search' --data-binary '---
query:
  function_score:
    field_value_factor:
      field: rank
'
```

You should see next results:

```
hits:
  total: 5
  max_score: 4.5
  hits:
  - _index: "bikeshop"
    _type: "_doc"
    _id: "1"
    _score: 4.5
    _routing: "giant"
    _source:
      name: "Giant XTC jr 26+"
      manufacturer: "giant"
      rank: 4.5
  - _index: "bikeshop"
    _type: "_doc"
    _id: "2"
    _score: 4.4
    _routing: "giant"
    _source:
      name: "Giant XTC jr 24+"
      manufacturer: "giant"
      rank: 4.4
  - _index: "bikeshop"
    _type: "_doc"
    _id: "3"
    _score: 4.2
    _routing: "norco"
    _source:
      name: "Norco CHARGER 4.1"
      manufacturer: "norco"
      rank: 4.2
  - _index: "bikeshop"
    _type: "_doc"
    _id: "4"
    _score: 4.1
    _routing: "norco"
    _source:
      name: "Norco STORM 4.1"
      manufacturer: "norco"
      rank: 4.1
  - _index: "bikeshop"
    _type: "_doc"
    _id: "5"
    _score: 4.0
    _routing: "gt"
    _source:
      name: "GT STOMPER MAX 26"
      manufacturer: "gt"
      rank: 4.0
```

If I were GT manufacturer I would very angry that all my bikes are at the bottom positions.

So let's fix that:

```
curl -X GET -H 'Content-Type: application/yaml' 'localhost:9200/bikeshop/_search' --data-binary '---
query:
  function_score:
    field_value_factor:
      field: rank
rescore:
  window_size: 1000
  grouping_mixup:
    group_field: manufacturer
    decline_script:
      lang: grouping_mixup_scripts
      source: position_recip
'
```

Now all the manufacturers should be satisfied:

```
hits:
  total: 5
  max_score: 4.5
  hits:
  - _index: "bikeshop"
    _type: "_doc"
    _id: "1"
    _score: 4.5
    _routing: "giant"
    _source:
      name: "Giant XTC jr 26+"
      manufacturer: "giant"
      rank: 4.5
  - _index: "bikeshop"
    _type: "_doc"
    _id: "3"
    _score: 4.2
    _routing: "norco"
    _source:
      name: "Norco CHARGER 4.1"
      manufacturer: "norco"
      rank: 4.2
  - _index: "bikeshop"
    _type: "_doc"
    _id: "5"
    _score: 4.0
    _routing: "gt"
    _source:
      name: "GT STOMPER MAX 26"
      manufacturer: "gt"
      rank: 4.0
  - _index: "bikeshop"
    _type: "_doc"
    _id: "2"
    _score: 2.2
    _routing: "giant"
    _source:
      name: "Giant XTC jr 24+"
      manufacturer: "giant"
      rank: 4.4
  - _index: "bikeshop"
    _type: "_doc"
    _id: "4"
    _score: 2.05
    _routing: "norco"
    _source:
      name: "Norco STORM 4.1"
      manufacturer: "norco"
      rank: 4.1
```

`position_recip` is a reciprocal function that calculates new scores according to the formula:

```
new_score = score * (m / (a * pos + b) + c)
```

Where `pos` is a document position within its own group.


The variable have next values by default: `m = 1.0, a = 1.0, b = 1.0, c = 0.0`

Just look at the plot of the function with default values:
[1 / (x + 1)](https://www.wolframalpha.com/input/?i=plot+1+%2F+(x+%2B+1),+x+%3D+-1..10)

Also you can specify these variables via script parameters, for example:

```
decline_script:
  lang: grouping_mixup_scripts
  source: position_recip
  params:
    m: 1.0
    a: 1.0
    b: 2.0
    c: 0.5
```

As you can see this function has an asymptote `y = 0.5`:
[1 / (x + 2) + 0.5](https://www.wolframalpha.com/input/?i=plot+1+%2F+(x+%2B+2)+%2B+0.5,+x+%3D+-1..10)

If you need you can use your own script:

```
curl -X GET -H 'Content-Type: application/yaml' 'localhost:9200/bikeshop/_search' --data-binary '---
query:
  function_score:
    field_value_factor:
      field: rank
rescore:
  window_size: 1000
  grouping_mixup:
    group_field: manufacturer
    decline_script:
      lang: painless
      source: |
        params.pos < 4 ? (1 / (params.pos + 2) + 0.5) : (1 / (params.pos + 1))
'
```
