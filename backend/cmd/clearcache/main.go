package main

import (
	"context"
	"fmt"

	"github.com/go-redis/redis/v8"
)

func main() {
	rdb := redis.NewClient(&redis.Options{
		Addr:     "192.168.100.239:6379",
		Password: "123123",
		DB:       0,
	})
	ctx := context.Background()

	keys := []string{
		"home:page",
		"ranking:hot:full",
		"ranking:rising:full",
		"ranking:rating:full",
		"rankings:api:hot",
		"rankings:api:rising",
		"rankings:api:rating",
		"banners:active",
	}

	for _, k := range keys {
		result, err := rdb.Del(ctx, k).Result()
		if err != nil {
			fmt.Printf("DEL %s: error %v\n", k, err)
		} else {
			fmt.Printf("DEL %s: %d\n", k, result)
		}
	}
	fmt.Println("Cache cleared!")
}
