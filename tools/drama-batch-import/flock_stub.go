//go:build !unix

package main

import "fmt"

func acquireLock(lockPath string) (func(), error) {
	return nil, fmt.Errorf("drama-import: file locking requires unix (linux/darwin)")
}
