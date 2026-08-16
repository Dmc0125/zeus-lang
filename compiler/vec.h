#ifndef VEC_H
#define VEC_H

#define DEFINE_VEC(Type, Name)                                                 \
  typedef struct {                                                             \
    Type *items;                                                               \
    size_t count;                                                              \
    size_t capacity;                                                           \
  } Name;                                                                      \
  void Name##_init(Name *vec, size_t capacity);                                \
  void Name##_add(Name *vec, Type item);                                       \
  void Name##_clear(Name *vec);

#define DEFINE_VEC_IMPL(Type, Name)                                            \
  void Name##_init(Name *vec, size_t capacity) {                               \
    if (capacity == 0) {                                                       \
      capacity = 1;                                                            \
    }                                                                          \
    vec->items = (Type *)malloc(capacity * sizeof(Type));                      \
    if (vec->items == NULL) {                                                  \
      fprintf(stderr, "Failed to allocate memory for \"%s\"\n", #Name);        \
      exit(1);                                                                 \
    }                                                                          \
    vec->count = 0;                                                            \
    vec->capacity = capacity;                                                  \
  }                                                                            \
                                                                               \
  void Name##_add(Name *vec, Type item) {                                      \
    if (vec->count == vec->capacity) {                                         \
      size_t new_cap = vec->capacity * 2;                                      \
      vec->items = (Type *)realloc(vec->items, new_cap * sizeof(Type));        \
      if (vec->items == NULL) {                                                \
        fprintf(stderr, "Failed to allocate memory for \"%s\"\n", #Name);      \
        exit(1);                                                               \
      }                                                                        \
      vec->capacity = new_cap;                                                 \
    }                                                                          \
    vec->items[vec->count++] = item;                                           \
  }                                                                            \
                                                                               \
  void Name##_clear(Name *vec) { vec->count = 0; }

#endif
